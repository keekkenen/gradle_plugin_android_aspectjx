/*
 * Copyright 2018 firefly1126, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.gradle_plugin_android_aspectjx
 */
package com.hujiang.gradle.plugin.android.aspectjx.internal.cache

import com.hujiang.gradle.plugin.android.aspectjx.AJXConfig
import com.hujiang.gradle.plugin.android.aspectjx.AJXExtension
import com.hujiang.gradle.plugin.android.aspectjx.internal.AJXUtils
import com.hujiang.gradle.plugin.android.aspectjx.internal.TimeTrace
import com.hujiang.gradle.plugin.android.aspectjx.internal.model.AJXExtensionConfig
import org.apache.commons.io.FileUtils
import org.aspectj.weaver.Dump
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

/**
 * class description here
 * @author simon* @version 1.0.0* @since 2018-04-03
 */
class AJXCache {

    Project project
    String cachePath

    String extensionConfigPath
    AJXExtensionConfig ajxExtensionConfig = new AJXExtensionConfig()

    //for aspectj
    String encoding
    String bootClassPath
    String sourceCompatibility
    String targetCompatibility

    AJXCache(Project proj) {
        this.project = proj
        init()

        def ajxCache = this
        def configuration = new AJXConfig(project)
        project.afterEvaluate {
            def variants = configuration.variants
            if (variants && !variants.isEmpty()) {
                def variant = variants[0]
                JavaCompile javaCompile
                if (variant.hasProperty('javaCompileProvider')) {
                    //android gradle 3.3.0 +
                    javaCompile = variant.javaCompileProvider.get()
                } else {
                    javaCompile = variant.javaCompile
                }

                ajxCache.encoding = javaCompile.options.encoding
                ajxCache.sourceCompatibility = javaCompile.sourceCompatibility
                ajxCache.targetCompatibility = javaCompile.targetCompatibility
            }
            ajxCache.bootClassPath = configuration.bootClasspath.join(File.pathSeparator)

            AJXExtension ajxExtension = project.aspectjx
            project.logger.warn "[ajx] project.aspectjx=${ajxExtension}"

            //当过滤条件发生变化，clean掉编译缓存
            def isExtensionChanged = ajxCache.isExtensionChanged(ajxExtension)
            project.logger.warn("[ajx] isExtensionChanged=" + isExtensionChanged)
            if (isExtensionChanged) {
                project.logger.warn("[ajx] cache changed, clean before preBuild")
                project.tasks.findByName('preBuild').dependsOn(project.tasks.findByName("clean"))
            }

            ajxCache.putExtensionConfig(ajxExtension)

            // 设置运行变量
            System.setProperty("aspectj.multithreaded", "true")
            // set aspectj build log output dir
            File logDir = new File(project.buildDir.absolutePath + File.separator + "outputs"
                    + File.separator + "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            Dump.setDumpDirectory(logDir.absolutePath)
        }
    }

    private void init() {
        cachePath = project.buildDir.absolutePath + File.separator + "intermediates/ajx"
        extensionConfigPath = cachePath + File.separator + "extensionConfig.json"

        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        //extension config
        File extensionConfig = new File(extensionConfigPath)
        if (extensionConfig.exists()) {
            ajxExtensionConfig = AJXUtils.optFromJsonString(FileUtils.readFileToString(extensionConfig), AJXExtensionConfig.class)
        }

        if (ajxExtensionConfig == null) {
            ajxExtensionConfig = new AJXExtensionConfig()
        }

        if (ajxExtensionConfig.timeTraceEnabled) {
            project.gradle.addListener(new TimeTrace())
        }
    }

    File getCacheDir() {
        return new File(cachePath)
    }

    File getExtensionConfigFile() {
        return new File(extensionConfigPath)
    }

    void reset() {
        FileUtils.deleteDirectory(cacheDir)

        init()
    }

    void commit() {
        project.logger.debug("putExtensionConfig:${extensionConfigFile}")

        FileUtils.deleteQuietly(extensionConfigFile)

        File parent = extensionConfigFile.parentFile

        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        if (!extensionConfigFile.exists()) {
            extensionConfigFile.createNewFile()
        }

        String jsonString = AJXUtils.optToJsonString(ajxExtensionConfig)
        project.logger.debug("${jsonString}")
        FileUtils.write(extensionConfigFile, jsonString, "UTF-8")
    }

    void putExtensionConfig(AJXExtension extension) {
        if (extension == null) {
            return
        }

        ajxExtensionConfig.enabled = extension.enabled
        ajxExtensionConfig.timeTraceEnabled = extension.timeTraceEnabled
        ajxExtensionConfig.ajcArgs = extension.ajcArgs
        ajxExtensionConfig.includes = extension.includes
        ajxExtensionConfig.excludes = extension.excludes
    }

    boolean isExtensionChanged(AJXExtension extension) {
        if (extension == null) {
            return true
        }

        boolean isSourceIncludesExists = ajxExtensionConfig.includes != null && !ajxExtensionConfig.includes.isEmpty()
        boolean isTargetIncludeExists = extension.includes != null && !extension.includes.isEmpty()
        boolean isSourceExcludeExists = ajxExtensionConfig.excludes != null && !ajxExtensionConfig.excludes.isEmpty()
        boolean isTargetExcludeExists = extension.excludes != null && !extension.excludes.isEmpty()

        if ((!isSourceIncludesExists && isTargetIncludeExists)
                || (isSourceIncludesExists && !isTargetIncludeExists)
                || (!isSourceExcludeExists && isTargetExcludeExists)
                || (isSourceExcludeExists && !isTargetExcludeExists)) {
            return true
        }

        if ((!isSourceIncludesExists && !isTargetIncludeExists)
                && (!isSourceExcludeExists && !isTargetExcludeExists)) {
            return false
        }

        if (ajxExtensionConfig.includes.size() != extension.includes.size()
                || ajxExtensionConfig.excludes.size() != extension.excludes.size()) {
            return true
        }

        boolean isChanged = false
        ajxExtensionConfig.includes.each { String source ->
            boolean targetMatched = false
            for (String target : extension.includes) {
                if (source == target) {
                    targetMatched = true
                    break
                }
            }

            if (!targetMatched) {
                isChanged = true
            }
        }

        ajxExtensionConfig.excludes.each { String source ->
            boolean targetMatched = false
            for (String target : extension.excludes) {
                if (source == target) {
                    targetMatched = true
                    break
                }
            }

            if (!targetMatched) {
                isChanged = true
            }
        }

        return isChanged
    }
}
