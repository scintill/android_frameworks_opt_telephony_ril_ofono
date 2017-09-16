package org.ofono;

import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Variant;

import java.util.Map;
public final class Struct1 extends Struct
{
   @Position(0)
   public final Path a;
   @Position(1)
   public final Map<String,Variant> b;
  public Struct1(Path a, Map<String,Variant> b)
  {
   this.a = a;
   this.b = b;
  }
}
