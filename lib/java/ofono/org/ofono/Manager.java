package org.ofono;
import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
public interface Manager extends DBusInterface
{
   public static class ModemAdded extends DBusSignal
   {
      public final DBusInterface path;
      public final Map<String,Variant> properties;
      public ModemAdded(String pathStr, DBusInterface path, Map<String,Variant> properties) throws DBusException
      {
         super(pathStr, path, properties);
         this.path = path;
         this.properties = properties;
      }
   }
   public static class ModemRemoved extends DBusSignal
   {
      public final DBusInterface path;
      public ModemRemoved(String pathStr, DBusInterface path) throws DBusException
      {
         super(pathStr, path);
         this.path = path;
      }
   }

  public List<PathAndProperties> GetModems();

}
