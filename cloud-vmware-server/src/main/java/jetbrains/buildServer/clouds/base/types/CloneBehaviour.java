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

package jetbrains.buildServer.clouds.base.types;

/**
 * @author Sergey.Pak
 *         Date: 9/18/2014
 *         Time: 1:23 PM
 */
public enum CloneBehaviour {
  START_STOP (false, true),
  FRESH_CLONE (true, false),
  ON_DEMAND_CLONE(false, false)
  ;
  private final boolean myDeleteAfterStop;
  private final boolean myUseOriginal;

  CloneBehaviour(final boolean deleteAfterStop, final boolean useOriginal) {
    myDeleteAfterStop = deleteAfterStop;
    myUseOriginal = useOriginal;
  }

  public boolean isDeleteAfterStop() {
    return myDeleteAfterStop;
  }

  public boolean isUseOriginal() {
    return myUseOriginal;
  }
}
