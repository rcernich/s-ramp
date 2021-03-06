/*
 * Copyright 2011 JBoss Inc
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
package org.overlord.sramp.repository;

import java.util.ServiceLoader;

import org.overlord.sramp.repository.i18n.Messages;


public class DerivedArtifactsFactory {

    public static DerivedArtifacts newInstance()  {

        for (DerivedArtifacts manager : ServiceLoader.load(DerivedArtifacts.class)) {
            return manager;
        }
        throw new RuntimeException(Messages.i18n.format("MISSING_DERIVED_ARTIFACTS_PROVIDER")); //$NON-NLS-1$
    }
}
