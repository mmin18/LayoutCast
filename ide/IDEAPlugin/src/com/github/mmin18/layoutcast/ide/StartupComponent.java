package com.github.mmin18.layoutcast.ide;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mmin18 on 7/29/15.
 */
public class StartupComponent implements ApplicationComponent {
    public static byte[] data;

    public StartupComponent() {
    }

    public void initComponent() {
        new Thread() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://raw.githubusercontent.com/mmin18/LayoutCast/master/cast.py");
                    InputStream ins = url.openConnection().getInputStream();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int l;
                    while ((l = ins.read(buf)) != -1) {
                        bos.write(buf, 0, l);
                    }
                    ins.close();
                    bos.close();
                    String str = new String(bos.toByteArray(), "utf-8");
                    Pattern p = Pattern.compile("^__plugin__\\s*=\\s*['\"](\\d+)['\"]", Pattern.MULTILINE);
                    Matcher m = p.matcher(str);
                    if (m.find()) {
                        String s = m.group(1);
                        if ("1".equals(s)) {
                            data = bos.toByteArray();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public void disposeComponent() {
    }

    @NotNull
    public String getComponentName() {
        return "StartupComponent";
    }
}
