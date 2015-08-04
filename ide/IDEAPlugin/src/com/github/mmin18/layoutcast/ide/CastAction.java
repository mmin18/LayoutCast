package com.github.mmin18.layoutcast.ide;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.impl.ProjectRunConfigurationManager;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.PathUtil;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.io.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by mmin18 on 7/29/15.
 */
public class CastAction extends AnAction {
    Process running;
    long runTime;

    public void actionPerformed(final AnActionEvent e) {
        if (running != null && System.currentTimeMillis() - runTime < 5000) {
            return;
        }

        Project currentProject = DataKeys.PROJECT.getData(e.getDataContext());

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

        try {
            File cp = new File(PathUtil.getJarPathForClass(getClass()));
            if (cp.isDirectory()) {
                File file = new File(cp, "cast.py");
                InputStream ins = new FileInputStream(file);
                double v = readVersion(ins);
                ins.close();
                if (v > projectCastVersion) {
                    castPy = file;
                }
            } else {
                ZipFile zf = new ZipFile(cp);
                ZipEntry ze = zf.getEntry("cast.py");
                InputStream ins = zf.getInputStream(ze);
                double v = readVersion(ins);
                ins.close();
                if (v > projectCastVersion) {
                    File tmp = File.createTempFile("lcast", "" + v);
                    FileOutputStream fos = new FileOutputStream(tmp);
                    ins = zf.getInputStream(ze);
                    byte[] buf = new byte[4096];
                    int l;
                    while ((l = ins.read(buf)) != -1) {
                        fos.write(buf, 0, l);
                    }
                    fos.close();
                    ins.close();

                    castPy = tmp;
                }
            }
        } catch (Exception ex) {
        }

        final File finalCastPy = castPy;
        new Thread() {
            int exit = -1;
            String output = "";

            @Override
            public void run() {
                try {
                    if (running != null) {
                        running.destroy();
                    }
                    File androidSdk = getAndroidSdk();
                    ArrayList<String> args = new ArrayList<String>();
                    args.add("python");
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
                    exit = -1;
                    output = e.toString();
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
        }.start();
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

    private static final Pattern R_VER = Pattern.compile("^__version__\\s*=\\s*['\"](\\d+\\.\\d+)['\"]", Pattern.MULTILINE);

    private double readVersion(InputStream ins) throws Exception {
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
