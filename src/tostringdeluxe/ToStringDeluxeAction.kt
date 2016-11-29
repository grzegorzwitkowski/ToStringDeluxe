package tostringdeluxe

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR
import com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil

class ToStringDeluxeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val activeFile = e.getData(PSI_FILE)!!
        val activeCodeElement = activeFile.findElementAt(e.getData(EDITOR)!!.caretModel.offset)
        val project = e.project!!
        val elementFactory = JavaPsiFacade.getElementFactory(project)
        val targetClass = PsiTreeUtil.getParentOfType(activeCodeElement, PsiClass::class.java)!!
        val generatedMethod = elementFactory.createMethodFromText(toStringMethodBody(), targetClass)
        val formattedMethod = CodeStyleManager.getInstance(project).reformat(generatedMethod)

        object : WriteCommandAction.Simple<Project>(project, activeFile) {
            override fun run() {
                targetClass.add(formattedMethod)
            }
        }.execute()
    }

    private fun toStringMethodBody(): String {
        return """@Override public String toString() { return ""; }"""
    }
}
