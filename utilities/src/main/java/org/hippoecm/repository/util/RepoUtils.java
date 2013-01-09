/*
 *  Copyright 2012-2013 Hippo B.V. (http://www.onehippo.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.hippoecm.repository.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.Manifest;

import org.apache.commons.io.IOUtils;

/**
 * Utility methods used by the repository
 */
public class RepoUtils {

    /**
     * If the path starts with a file: protocol prefix convert it to an absolute filesystem path
     *
     * @param path  the path to stripped
     * @return  the path stripped of the file: protocol prefix
     */
    public static String stripFileProtocol(String path) {
        if (path.startsWith("file://")) {
            return path.substring(6);
        } else if (path.startsWith("file:/")) {
            return path.substring(5);
        } else if (path.startsWith("file:")) {
            return "/" + path.substring(5);
        }
        return path;
    }

    /**
     * @param clazz the class object for which to obtain a reference to the manifest
     * @return the URL of the manifest found, or {@code null} if it could not be obtained
     */
    public static URL getManifestURL(Class clazz) {
        try {
            final StringBuilder sb = new StringBuilder();
            final String[] classElements = clazz.getName().split("\\.");
            for (int i=0; i<classElements.length-1; i++) {
                sb.append("../");
            }
            sb.append("META-INF/MANIFEST.MF");
            final URL classResource = clazz.getResource(classElements[classElements.length-1]+".class");
            if (classResource != null) {
                return new URL(classResource, new String(sb));
            }
        } catch (MalformedURLException ignore) {
        }
        return null;
    }

    /**
     *
     * @param clazz  the class object for which to obtain the manifest
     * @return  the manifest object, or {@code null} if it could not be obtained
     * @throws IOException  if something went wrong while reading the manifest
     */
    public static Manifest getManifest(Class clazz) throws IOException {
        final URL url = getManifestURL(clazz);
        if (url != null) {
            final InputStream is = url.openStream();
            try {
                return new Manifest(is);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
        return null;
    }

}
