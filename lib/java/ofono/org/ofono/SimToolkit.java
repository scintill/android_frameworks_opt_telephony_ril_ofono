package org.ofono;
import java.util.Map;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
public interface SimToolkit extends DBusInterface
{
   public static class PropertyChanged extends DBusSignal
   {
      public final String name;
      public final Variant value;
      public PropertyChanged(String path, String name, Variant value) throws DBusException
      {
         super(path, name, value);
         this.name = name;
         this.value = value;
      }
   }

  public Map<String,Variant> GetProperties();
  public void SelectItem(byte item, DBusInterface agent);
  public void RegisterAgent(DBusInterface path);
  public void UnregisterAgent(DBusInterface path);

}
