/*
 * Copyright (C) 2013 Square, Inc.
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
package com.squareup.rack.jruby;

import com.squareup.rack.RackInput;
import java.io.IOException;
import org.jcodings.Encoding;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

/**
 * <p>Adapts a {@link com.squareup.rack.RackInput} into Ruby-space.</p>
 *
 * <p>Is primarily responsible for translating Java byte arrays into Ruby Strings with
 * {@code ASCII-8BIT} encoding.</p>
 */
public class JRubyRackInput extends RubyObject {
  private RackInput rackInput;
  private Encoding ascii8bitEncoding;

  private static final ObjectAllocator ALLOCATOR = new ObjectAllocator() {
    public IRubyObject allocate(Ruby runtime, RubyClass klass) {
      return new JRubyRackInput(runtime, klass);
    }
  };

  private static RubyClass getRackInputClass(Ruby runtime) {
    RubyModule module = runtime.getOrCreateModule("RackServlet");
    RubyClass klass = module.getClass("RackInput");
    if (klass == null) {
      klass = module.defineClassUnder("RackInput", runtime.getObject(), ALLOCATOR);
      klass.defineAnnotatedMethods(JRubyRackInput.class);
    }
    return klass;
  }

  JRubyRackInput(Ruby runtime, RubyClass klass) {
    super(runtime, klass);
  }

  /**
   * Creates a Ruby IO object that delegates to the given {@link RackInput}.
   *
   * @param runtime the Ruby runtime that will host this instance.
   * @param rackInput the backing data source.
   */
  public JRubyRackInput(Ruby runtime, RackInput rackInput) {
    super(runtime, getRackInputClass(runtime));
    this.rackInput = rackInput;
    this.ascii8bitEncoding = runtime.getEncodingService().getAscii8bitEncoding();
  }

  @JRubyMethod public IRubyObject gets() {
    try {
      return toRubyString(rackInput.gets());
    } catch (IOException e) {
      throw getRuntime().newIOErrorFromException(e);
    }
  }

  @JRubyMethod public IRubyObject each(ThreadContext context, Block block) {
    IRubyObject nil = getRuntime().getNil();
    IRubyObject line;
    while ((line = gets()) != nil) {
      block.yield(context, line);
    }
    return nil;
  }

  @JRubyMethod(optional = 1) public IRubyObject read(ThreadContext context, IRubyObject[] args) {
    Integer length = null;

    if (args.length > 0) {
      long arg = args[0].convertToInteger("to_i").getLongValue();
      length = (int) Math.min(arg, Integer.MAX_VALUE);
    }

    try {
      return toRubyString(rackInput.read(length));
    } catch (IOException e) {
      throw getRuntime().newIOErrorFromException(e);
    }
  }

  @JRubyMethod public IRubyObject rewind() {
    try {
      rackInput.rewind();
    } catch (IOException e) {
      throw getRuntime().newIOErrorFromException(e);
    }
    return getRuntime().getNil();
  }

  private IRubyObject toRubyString(byte[] bytes) {
    if (bytes == null) {
      return getRuntime().getNil();
    } else {
      return RubyString.newString(getRuntime(), new ByteList(bytes, ascii8bitEncoding));
    }
  }
}
