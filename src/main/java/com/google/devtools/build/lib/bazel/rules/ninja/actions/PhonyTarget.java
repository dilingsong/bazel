// Copyright 2020 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.bazel.rules.ninja.actions;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Helper class to represent "evaluated" phony target: it contains the List with direct non-phony
 * inputs to the phony target (PathFragments), the list with direct phony inputs; and it contains
 * the flag whether this phony target is always dirty, i.e. must be rebuild each time.
 *
 * <p>Always-dirty phony targets are those which do not have any inputs: "build alias: phony". All
 * usual direct dependants of those actions automatically also always-dirty (but not the transitive
 * dependants: they should check whether their computed inputs have changed). As phony targets are
 * not performing any actions, <b>all phony transitive dependants of always-dirty phony targets are
 * themselves always-dirty.</b> That is why we can compute the always-dirty flag for the phony
 * targets, and use it for marking their direct non-phony dependants as actions to be executed
 * unconditionally.
 */
@Immutable
public final class PhonyTarget {
  private final ImmutableList<PathFragment> phonyNames;
  private final ImmutableList<PathFragment> directUsualInputs;
  private final boolean isAlwaysDirty;

  public PhonyTarget(
      ImmutableList<PathFragment> phonyNames,
      ImmutableList<PathFragment> directUsualInputs,
      boolean isAlwaysDirty) {
    this.phonyNames = phonyNames;
    this.directUsualInputs = directUsualInputs;
    this.isAlwaysDirty = isAlwaysDirty;
  }

  public ImmutableList<PathFragment> getPhonyNames() {
    return phonyNames;
  }

  public ImmutableList<PathFragment> getDirectUsualInputs() {
    return directUsualInputs;
  }

  public boolean isAlwaysDirty() {
    return isAlwaysDirty;
  }

  public void visitUsualInputs(
      ImmutableSortedMap<PathFragment, PhonyTarget> phonyTargetsMap,
      Consumer<ImmutableList<PathFragment>> consumer) {
    consumer.accept(directUsualInputs);

    ArrayDeque<PathFragment> queue = new ArrayDeque<>(phonyNames);
    Set<PathFragment> visited = Sets.newHashSet();
    while (!queue.isEmpty()) {
      PathFragment fragment = queue.remove();
      if (visited.add(fragment)) {
        PhonyTarget phonyTarget = Preconditions.checkNotNull(phonyTargetsMap.get(fragment));
        consumer.accept(phonyTarget.getDirectUsualInputs());
        queue.addAll(phonyTarget.getPhonyNames());
      }
    }
  }
}
