/*
 * Druid - a distributed column store.
 * Copyright 2012 - 2015 Metamarkets Group Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.druid.common.guava;

import com.google.common.io.ByteSink;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
*/
public class FileOutputSupplier extends ByteSink
{
  private final File file;
  private final boolean append;

  public FileOutputSupplier(File file, boolean append)
  {
    this.file = file;
    this.append = append;
  }

  @Override
  public OutputStream openStream() throws IOException {
    return new FileOutputStream(file, append);
  }

  public File getFile()
  {
    return file;
  }
}
