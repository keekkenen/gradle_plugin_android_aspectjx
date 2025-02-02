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
// This plugin is based on https://github.com/JakeWharton/hugo
package com.hujiang.gradle.plugin.android.aspectjx

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * aspectj plugin,
 * @author simon
 * @version 1.0.0
 * @since 2016-04-20
 */
class AJXPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        project.repositories {
            mavenLocal()
        }

        project.dependencies {
            def gradleVersion = project.gradle.gradleVersion
            if (gradleVersion > "4.0") {
                implementation 'org.aspectj:aspectjrt:1.9.6'
            } else {
                compile 'org.aspectj:aspectjrt:1.9.6'
            }
        }

        project.extensions.create("aspectjx", AJXExtension)

        if (project.plugins.hasPlugin(AppPlugin)) {
            //register AspectTransform
            AppExtension android = project.extensions.getByType(AppExtension)
            project.logger.quiet("[ajx] register AJXTransform")
            android.registerTransform(new AJXTransform(project))
        }
    }
}
