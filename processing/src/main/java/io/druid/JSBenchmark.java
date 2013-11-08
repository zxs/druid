package io.druid;

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

import org.dynjs.Config;
import org.dynjs.compiler.JSCompiler;
import org.dynjs.parser.ast.FunctionDescriptor;
import org.dynjs.parser.ast.FunctionExpression;
import org.dynjs.parser.ast.ProgramTree;
import org.dynjs.parser.js.JavascriptParser;
import org.dynjs.runtime.DynJS;
import org.dynjs.runtime.ExecutionContext;
import org.dynjs.runtime.JSFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;

interface JSFunctionWrapper {
  public double apply(double... values);
  public void close();
}

public class JSBenchmark
{
  static class Result {
    public final double value;
    public final long time;

    public Result(double value, long time) {
      this.value = value;
      this.time = time;
    }

    @Override
    public String toString()
    {
      return "value=" + value +
             ", time=" + time + "ms";
    }
  }

  public static void main(String[] args)
  {
    String function = "function(delta, total) { return 100. * Math.abs(delta) / total; }";

    DynJSFunction fnDynJs = new DynJSFunction(function);
    RhinoFunction fnRhino = new RhinoFunction(function);

    final int warmup = 1_000_000;
    final int iterations = 4_000_000;

    System.out.println("DynJS:  " + time(fnDynJs, warmup, iterations));
    System.out.println("Rhino:  " + time(fnRhino, warmup, iterations));
    System.out.println("Native: " + time(new NativeFunction(), warmup, iterations));
  }

  public static Result time(JSFunctionWrapper fn, int warmup, int iterations) {
    double x = 0;
    for (int i = 0; i < warmup; ++i) {
      x += fn.apply(i, 1000.0);
    }

    long t0 = System.currentTimeMillis();
    for (int i = 0; i < iterations; ++i) {
      x += fn.apply(i, 1000.0);
    }
    long t = System.currentTimeMillis() - t0;
    return new Result(x, t);
  }
}

class RhinoFunction implements JSFunctionWrapper
{
  final ContextFactory contextFactory;
  final ScriptableObject scope;
  final Function function;

  public RhinoFunction(String script)
  {
    contextFactory = ContextFactory.getGlobal();
    Context context = contextFactory.enterContext();
    context.setOptimizationLevel(9);

    scope = context.initStandardObjects();
    function = context.compileFunction(scope, script, "fn", 1, null);

    Context.exit();
  }

  @Override
  public double apply(double... values)
  {
    Context cx = Context.getCurrentContext();
    if (cx == null) {
      cx = contextFactory.enterContext();
    }

    Object[] args = new Object[values.length];
    int i = 0;
    while(i < values.length) {
      args[i] = values[i++];
    }

    return Context.toNumber(function.call(cx, scope, scope, args));
  }

  @Override
  public void close()
  {
    if (Context.getCurrentContext() != null) {
      Context.exit();
    }
  }
}

class DynJSFunction implements JSFunctionWrapper
{
  final JSFunction function;
  final ExecutionContext context;

  public DynJSFunction(String script)
  {
    Config config = new Config();
    config.setCompileMode(Config.CompileMode.JIT);

    final DynJS dynjs = new DynJS(config);
    context = dynjs.getExecutionContext();

    JavascriptParser parser= new JavascriptParser(context);
    ProgramTree tree = parser.parse("var fn = " + script + ";");

    FunctionDescriptor fnDescriptor = ((FunctionExpression)tree.getVariableDeclarations().get(0).getExpr()).getDescriptor();

    JSCompiler compiler = context.getCompiler();
    this.function = compiler.compileFunction(
        context,
        fnDescriptor.getIdentifier(),
        fnDescriptor.getFormalParameterNames(),
        fnDescriptor.getBlock(),
        true
    );
  }

  public double apply(double... values) {
    Object[] args = new Object[values.length];
    int i = 0;
    while(i < values.length) {
      args[i] = values[i++];
    }
    return ((Number)context.call(function, null, args)).doubleValue();
  }

  @Override
  public void close()
  {
    // cleanup resources
  }
}

class NativeFunction implements JSFunctionWrapper {
  @Override
  public double apply(double... values)
  {
    return 100. * Math.abs(values[0]) / values[1];
  }

  @Override
  public void close()
  {
    // nothing to cleanup
  }
}
