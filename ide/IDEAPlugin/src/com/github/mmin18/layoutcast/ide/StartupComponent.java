package com.github.mmin18.layoutcast.ide;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;

/**
 * Created by mmin18 on 7/29/15.
 */
public class StartupComponent implements ApplicationComponent {
    public StartupComponent() {
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    @NotNull
    public String getComponentName() {
        return "StartupComponent";
    }
}
