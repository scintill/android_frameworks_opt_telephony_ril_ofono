package org.ofono;
import java.util.Map;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
public interface CallSettings extends DBusInterface
{
   public static class PropertyChanged extends DBusSignal
   {
      public final String property;
      public final Variant value;
      public PropertyChanged(String path, String property, Variant value) throws DBusException
      {
         super(path, property, value);
         this.property = property;
         this.value = value;
      }
   }

  public Map<String,Variant> GetProperties();
  public void SetProperty(String property, Variant value);

}
