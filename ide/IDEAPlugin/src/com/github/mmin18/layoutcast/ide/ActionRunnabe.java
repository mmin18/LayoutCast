package com.github.mmin18.layoutcast.ide;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
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

/**
 *
 * The thread from CastAction.java has been separated into this modificated Runnable implementation.
 *
 * Created by 3mill on 2015-08-19.
 */
public class ActionRunnabe implements Runnable {
    /**
     * Enum for chosing python command.
     */
    public enum PythonCommand{

      PYTHON("python"),PY("py"),PYTHONEXE("python.exe");

        private String pythonCommandString;

        PythonCommand(String pythonCommandString) {
            this.pythonCommandString = pythonCommandString;
        }

        public String getCommandString(){
            return pythonCommandString;
        }
    }

    private static final String CANNOT_RUN_PROGRAM_ERROR="Cannot run program";


    int exit = -1;
    String output = "";


    private Process running;
    private long runTime;
    private File dir;
    private File finalCastPy;
    private AnActionEvent e;

    public ActionRunnabe(Process running, long runTime, File dir, File finalCastPy, AnActionEvent e) {
        this.running = running;
        this.runTime = runTime;
        this.dir = dir;
        this.finalCastPy = finalCastPy;
        this.e = e;
    }

    @Override
    public void run() {
        executeAction(PythonCommand.PYTHON);
    }


    private void executeAction(PythonCommand pythonCommand){
        try {
            if (running != null) {
                running.destroy();
            }
            File androidSdk = getAndroidSdk();
            ArrayList<String> args = new ArrayList<String>();
            args.add(pythonCommand.getCommandString());
            args.add(finalCastPy.getAbsolutePath());
            if (androidSdk != null) {
                args.add("--sdk");
                args.add(androidSdk.getAbsolutePath());
            }
            args.add(dir.getAbsolutePath());
            Process p = Runtime.getRuntime().exec(args.toArray(new String[0]), null, dir);
            running = p;
            runTime = System.currentTimeMillis();
            InputStream ins = p.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int l;
            while ((l = ins.read(buf)) != -1) {
                bos.write(buf, 0, l);
            }
            ins.close();
            if (bos.size() > 0) {
                bos.write('\n');
            }
            ins = p.getErrorStream();
            while ((l = ins.read(buf)) != -1) {
                bos.write(buf, 0, l);
            }
            ins.close();
            exit = p.waitFor();
            output = new String(bos.toByteArray());
        } catch (Exception e) {
            /*In case when command was not recognized, the executeAction methos will be execute with next one.
                In case when it fails with last one, the default error handling will be called.
             */
            if (e instanceof IOException && e.getMessage().startsWith(CANNOT_RUN_PROGRAM_ERROR)){
                switch (pythonCommand){
                    case PYTHON:
                        executeAction(PythonCommand.PY);
                        break;
                    case PY:
                        executeAction(PythonCommand.PYTHONEXE);
                        break;
                    case PYTHONEXE:
                        handleError();
                        break;
                }
            } else {
                handleError();
            }
        } finally {
            running = null;
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                if (exit != 0 && output.length() > 1512) {
                    try {
                        File tmp = File.createTempFile("lcast_log", ".txt");
                        FileOutputStream fos = new FileOutputStream(tmp);
                        fos.write(output.getBytes());
                        fos.close();

                        output = output.substring(0, 1500) + "...";
                        output += "\n<a href=\"file://" + tmp.getAbsolutePath() + "\">see log</a>";
                    } catch (Exception e) {
                    }
                }

                StatusBar statusBar = WindowManager.getInstance()
                        .getStatusBar(DataKeys.PROJECT.getData(e.getDataContext()));
                JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder(output, exit == 0 ? MessageType.INFO : MessageType.ERROR, new HyperlinkListener() {
                            @Override
                            public void hyperlinkUpdate(HyperlinkEvent e) {
                                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                    try {
                                        java.awt.Desktop.getDesktop().browse(e.getURL().toURI());
                                    } catch (Exception ex) {
                                    }
                                }
                            }
                        })
                        .setFadeoutTime(exit == 0 ? 1500 : 6000)
                        .createBalloon()
                        .show(RelativePoint.getCenterOf(statusBar.getComponent()),
                                Balloon.Position.atRight);
            }
        });
    }
    //code from catch Exception has been moved here.
    private void handleError(){
        exit = -1;
        output = e.toString();
    }

    private static File getAndroidSdk() {
        try {
            ClassLoader cl = PluginManager.getPlugin(PluginId.getId("org.jetbrains.android")).getPluginClassLoader();
            Class c = cl.loadClass("com.android.tools.idea.sdk.DefaultSdks");
            return (File) c.getMethod("getDefaultAndroidHome").invoke(null);
        } catch (Exception ex) {
        }
        return null;
    }

}
