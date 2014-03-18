/*
 * Druid - a distributed column store.
 * Copyright (C) 2012, 2013  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package io.druid.serde;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.metamx.common.Pair;
import io.druid.jackson.DefaultObjectMapper;
import io.druid.query.filter.SelectorDimFilter;
import io.druid.query.search.search.LexicographicSearchSortSpec;
import org.junit.Assert;
import org.junit.Test;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;

public class TestBasicSerde
{
  private static Multimap<Class, Pair<String, Object>> jsonRepresentations = ArrayListMultimap.create();
  private static ObjectMapper mapper = new DefaultObjectMapper();

  static {
    jsonRepresentations.put(
        LexicographicSearchSortSpec.class,
        new Pair("{\"type\":\"lexicographic\"}", new LexicographicSearchSortSpec())
    );
    jsonRepresentations.put(
        SelectorDimFilter.class,
        new Pair(
            "{\"type\":\"selector\",\"dimension\":\"abc\", \"value\": \"def\"}",
            new SelectorDimFilter("abc", "def")
        )
    );
  }

  @Test
  public void testAllJsonClasses() throws Exception
  {
    Reflections reflections = new Reflections("io.druid", new MethodAnnotationsScanner());
    Set<Constructor> constructors = reflections.getConstructorsAnnotatedWith(JsonCreator.class);
    Set<Class> classesNotTested = new HashSet<>();
    Set<Class> foundClasses = new HashSet<>();

    for (Constructor cnstr : constructors) {
      Class classToTest = cnstr.getDeclaringClass();
      if (jsonRepresentations.containsKey(classToTest)) {
        testClass(classToTest);
      } else {
        classesNotTested.add(classToTest);
      }
    }
    Assert.assertEquals(
        String.format("Please Add serde test for classes %s ", classesNotTested),
        0,
        classesNotTested.size()
    );
    System.out.println("Found classes : " + foundClasses);
  }

  private void testClass(Class klass) throws Exception
  {
    for (Pair<String, Object> jsonPair : jsonRepresentations.get(klass)) {
      // deserialize existing representation and verify equality
      Object deserialized = mapper.readValue(jsonPair.lhs, klass);
      Assert.assertEquals(deserialized, jsonPair.rhs);

      // serialize and deserialize the object and verify equality
      String objectAsString = mapper.writeValueAsString(deserialized);
      Object deserializedAgain = mapper.readValue(objectAsString, klass);
      Assert.assertEquals(deserializedAgain, jsonPair.rhs);

    }

  }


}
