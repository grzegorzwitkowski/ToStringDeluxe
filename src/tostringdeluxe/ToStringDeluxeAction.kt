package tostringdeluxe

import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.notification.NotificationDisplayType.BALLOON
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType.ERROR
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR
import com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints.BOTH
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ToStringDeluxeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val targetClass = getTargetClass(e)

        if (guavaIsOnModuleClasspath(targetClass, e.project!!)) {
            val fieldSelectionDialog = FieldSelectionDialog(targetClass)
            fieldSelectionDialog.show()
            if (fieldSelectionDialog.isOK) {
                val generatedCode = generateCode(e.project!!, targetClass, fieldSelectionDialog.selectedFields())
                object : WriteCommandAction.Simple<Project>(e.project, e.getData(PSI_FILE)!!) {
                    override fun run() {
                        targetClass.add(generatedCode)
                    }
                }.execute()
            }
        } else {
            val notificationGroup = NotificationGroup("toString Deluxe", BALLOON, false)
            val notification = notificationGroup.createNotification("Guava 18.0+ wasn't found on classpath.", ERROR)
            Notifications.Bus.notify(notification, e.project)
        }
    }

    private fun guavaIsOnModuleClasspath(targetClass: PsiClass, project: Project): Boolean {
        val module = ModuleUtil.findModuleForFile(targetClass.containingFile.virtualFile, project)!!
        val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false)
        return JavaPsiFacade.getInstance(project).findClass("com.google.common.base.MoreObjects", scope) != null
    }

    private fun getTargetClass(e: AnActionEvent): PsiClass {
        val activeCodeElement = e.getData(PSI_FILE)!!.findElementAt(e.getData(EDITOR)!!.caretModel.offset)
        return PsiTreeUtil.getParentOfType(activeCodeElement, PsiClass::class.java)!!
    }

    private fun generateCode(project: Project, targetClass: PsiClass, selectedFields: List<PsiField>): PsiElement {
        val elementFactory = JavaPsiFacade.getElementFactory(project)
        val generatedToString = elementFactory.createMethodFromText(toStringMethodBody(selectedFields), targetClass)
        val withShortenedImports = JavaCodeStyleManager.getInstance(project).shortenClassReferences(generatedToString)
        return CodeStyleManager.getInstance(project).reformat(withShortenedImports)
    }

    private fun toStringMethodBody(selectedFields: List<PsiField>): String {
        val addStatements = selectedFields.map { field ->
            "\n.add(\"${field.name}\", ${field.name})"
        }.joinToString(separator = "")

        return "@Override public String toString() {" +
                "return com.google.common.base.MoreObjects.toStringHelper(this)$addStatements\n.toString();" +
                "}"
    }
}

class FieldSelectionDialog(val targetClass: PsiClass) : DialogWrapper(targetClass.project) {

    private val fieldList = JBList<PsiField>(CollectionListModel(*targetClass.fields))
    private val rootPanel = JPanel(GridBagLayout())

    init {
        title = "Select fields to include in toString"
        fieldList.cellRenderer = DefaultPsiElementCellRenderer()
        val toolbarDecorator = ToolbarDecorator.createDecorator(fieldList).disableAddAction().disableRemoveAction()
        val labeledComponent = LabeledComponent.create(toolbarDecorator.createPanel(), "Fields to include in toString")
        val gridBagConstraints = defaultGridBagConstraints()
        rootPanel.add(labeledComponent, gridBagConstraints)
        init()
    }

    override fun createCenterPanel(): JComponent? {
        return rootPanel
    }

    fun selectedFields(): List<PsiField> = fieldList.selectedValuesList

    private fun defaultGridBagConstraints(): GridBagConstraints {
        val gridBagConstraints = GridBagConstraints()
        gridBagConstraints.gridx = 0
        gridBagConstraints.gridy = 0
        gridBagConstraints.gridwidth = 1
        gridBagConstraints.weightx = 1.0
        gridBagConstraints.weighty = 1.0
        gridBagConstraints.fill = BOTH
        return gridBagConstraints
    }
}
