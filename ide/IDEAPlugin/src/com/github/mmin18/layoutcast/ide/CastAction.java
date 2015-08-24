package com.github.mmin18.layoutcast.ide;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.io.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mmin18 on 7/29/15.
 */
public class CastAction extends AnAction {

    public void actionPerformed(final AnActionEvent e) {

        Project currentProject = DataKeys.PROJECT.getData(e.getDataContext());
        FileDocumentManager.getInstance().saveAllDocuments();

        final File dir = new File(currentProject.getBasePath());
        File castPy = null;

        double projectCastVersion = 0;
        File f = new File(dir, "cast.py");
        if (f.length() > 0) {
            try {
                FileInputStream fis = new FileInputStream(f);
                projectCastVersion = readVersion(fis);
                fis.close();
                castPy = f;
            } catch (Exception ex) {
            }
        }

        if (StartupComponent.getCastVersion() > projectCastVersion) {
            File ff = StartupComponent.getCastFile();
            if (ff != null) {
                castPy = ff;
            }
        }

        new Thread(new ActionRunnabe(dir, castPy, e)).start();
    }


    private static final Pattern R_VER = Pattern.compile("^__version__\\s*=\\s*['\"](\\d+\\.\\d+)['\"]", Pattern.MULTILINE);

    public static double readVersion(InputStream ins) throws Exception {
        byte[] buf = new byte[1024];
        int l = ins.read(buf);
        String str = new String(buf, 0, l, "utf-8");
        Matcher m = R_VER.matcher(str);
        if (m.find()) {
            String s = m.group(1);
            return Double.parseDouble(s);
        }
        return 0;
    }
}
