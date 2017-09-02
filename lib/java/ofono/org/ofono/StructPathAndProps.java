package org.ofono;
import java.util.Map;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Variant;
public final class StructPathAndProps extends Struct
{
   @Position(0)
   public final Path path;
   @Position(1)
   public final Map<String,Variant<?>> props;
  public StructPathAndProps(Path a, Map<String,Variant<?>> b)
  {
   this.path = a;
   this.props = b;
  }
}
