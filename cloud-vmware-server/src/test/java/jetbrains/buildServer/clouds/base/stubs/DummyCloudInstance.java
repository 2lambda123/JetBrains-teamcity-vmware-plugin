/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
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
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.base.stubs;

import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Sergey.Pak on 3/4/2016.
 */
public class DummyCloudInstance extends AbstractCloudInstance<DummyCloudImage> {

  protected DummyCloudInstance(@NotNull final DummyCloudImage image, @NotNull final String name, @NotNull final String instanceId) {
    super(image, name, instanceId);
  }

  @Override
  public boolean containsAgent(@NotNull final AgentDescription agent) {
    return getName().equals(agent.getAvailableParameters().get("name"));
  }
}
