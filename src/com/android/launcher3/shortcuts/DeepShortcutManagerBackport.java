/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.shortcuts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;

import com.android.launcher3.Utilities;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeepShortcutManagerBackport {
    static Drawable getShortcutIconDrawable(ShortcutInfoCompat shortcutInfo, int density) {
        return ((ShortcutInfoCompatBackport) shortcutInfo).getIcon(density);
    }

    public static List<ShortcutInfoCompat> getForPackage(Context context, LauncherApps mLauncherApps, ComponentName activity, String packageName) {
        List<ShortcutInfoCompat> shortcutInfoCompats = new ArrayList<>();
        if (Utilities.ATLEAST_MARSHMALLOW) {
            List<LauncherActivityInfo> infoList = mLauncherApps.getActivityList(packageName, android.os.Process.myUserHandle());
            for (LauncherActivityInfo info : infoList) {
                if (activity == null || activity.equals(info.getComponentName())) {
                    parsePackageXml(context, info.getComponentName().getPackageName(), info.getComponentName(), shortcutInfoCompats);
                }
            }
        }
        return shortcutInfoCompats;
    }

    private static void parsePackageXml(Context context, String packageName, ComponentName activity, List<ShortcutInfoCompat> shortcutInfoCompats) {
        PackageManager pm = context.getPackageManager();

        Intent intent = new Intent();
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setPackage(packageName);

        Set<String> exportedActivities = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(intent, 0)) {
            exportedActivities.add(ri.activityInfo.name);
        }

        String resource = null;
        String currActivity = "";
        String searchActivity = activity.getClassName();

        Map<String, String> parsedData = new HashMap<>();

        try {
            Resources resourcesForApplication = pm.getResourcesForApplication(packageName);
            AssetManager assets = resourcesForApplication.getAssets();
            XmlResourceParser parseXml = assets.openXmlResourceParser("AndroidManifest.xml");

            int eventType;
            while ((eventType = parseXml.nextToken()) != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String name = parseXml.getName();
                    if ("activity".equals(name) || "activity-alias".equals(name)) {
                        parsedData.clear();
                        for (int i = 0; i < parseXml.getAttributeCount(); i++) {
                            parsedData.put(parseXml.getAttributeName(i), parseXml.getAttributeValue(i));
                        }
                        if (parsedData.containsKey("name")) {
                            currActivity = parsedData.get("name");
                        }
                        if (parsedData.containsKey("exported")) {
                            String exported = parsedData.get("exported").toLowerCase();
                            if (exported.equals("true")) {
                                exportedActivities.add(currActivity);
                            } else if (exported.equals("false")) {
                                exportedActivities.remove(currActivity);
                            }
                        }
                    } else if (name.equals("meta-data") && currActivity.equals(searchActivity)) {
                        parsedData.clear();
                        for (int i = 0; i < parseXml.getAttributeCount(); i++) {
                            parsedData.put(parseXml.getAttributeName(i), parseXml.getAttributeValue(i));
                        }
                        if (parsedData.containsKey("name") &&
                                parsedData.get("name").equals("android.app.shortcuts") &&
                                parsedData.containsKey("resource")) {
                            resource = parsedData.get("resource");
                        }
                    }
                }
            }

            if (resource != null) {
                parseXml = resourcesForApplication.getXml(Integer.parseInt(resource.substring(1)));
                while ((eventType = parseXml.nextToken()) != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if (parseXml.getName().equals("shortcut")) {
                            ShortcutInfoCompat info = parseShortcut(context,
                                    activity,
                                    resourcesForApplication,
                                    packageName,
                                    parseXml);

                            if (info != null && info.getId() != null) {
                                for (ResolveInfo ri : pm.queryIntentActivities(ShortcutInfoCompatBackport.stripPackage(info.makeIntent()), 0)) {
                                    if (ri.isDefault || ri.activityInfo.exported) {
                                        shortcutInfoCompats.add(info);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException | XmlPullParserException | IOException e) {
            e.printStackTrace();
        }
    }

    private static ShortcutInfoCompat parseShortcut(Context context, ComponentName activity, Resources resourcesForApplication, String packageName, XmlResourceParser parseXml) {
        try {
            return new ShortcutInfoCompatBackport(context, resourcesForApplication, packageName, activity, parseXml);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
