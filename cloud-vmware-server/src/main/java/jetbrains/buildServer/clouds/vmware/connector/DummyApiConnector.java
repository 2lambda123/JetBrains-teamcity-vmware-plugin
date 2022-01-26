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

package jetbrains.buildServer.clouds.vmware.connector;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.vmware.VmwareCloudImage;
import jetbrains.buildServer.clouds.vmware.VmwareCloudInstance;
import org.jetbrains.annotations.NotNull;

/**
 * Created by sergeypak on 28/10/2016.
 */
public class DummyApiConnector implements CloudApiConnector<VmwareCloudImage, VmwareCloudInstance> {

  private final String myKey;

  public DummyApiConnector(final String key) {
    this.myKey = key;
  }


  @Override
  public void test() throws CheckedCloudException {
  }

  /**
   * A special myKey of the cloud connector. Used to determine whether this cloud connector can be used in several cloud profiles.
   * <br/>
   * <p>
   * It is supposed to represent the same username and server url/region/instance
   *
   * @return see above.
   */
  @NotNull
  @Override
  public String getKey() {
    return myKey;
  }

  @NotNull
  @Override
  public <R extends AbstractInstance> Map<String, R> fetchInstances(@NotNull final VmwareCloudImage image) throws CheckedCloudException {
    return Collections.emptyMap();
  }

  @NotNull
  @Override
  public <R extends AbstractInstance> Map<VmwareCloudImage, Map<String, R>> fetchInstances(@NotNull final Collection<VmwareCloudImage> images) throws CheckedCloudException {
    return Collections.emptyMap();
  }

  @NotNull
  @Override
  public TypedCloudErrorInfo[] checkImage(@NotNull final VmwareCloudImage image) {
    return new TypedCloudErrorInfo[0];
  }

  @NotNull
  @Override
  public Map<VmwareCloudImage, TypedCloudErrorInfo[]> checkImages(@NotNull final Collection<VmwareCloudImage> images) {
    return images.stream().collect(Collectors.toMap(Function.identity(), img->new TypedCloudErrorInfo[0]));
  }

  @NotNull
  @Override
  public TypedCloudErrorInfo[] checkInstance(@NotNull final VmwareCloudInstance instance) {
    return new TypedCloudErrorInfo[0];
  }
}
