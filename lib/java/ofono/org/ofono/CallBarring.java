package org.ofono;
import java.util.Map;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
public interface CallBarring extends DBusInterface
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
  public void SetProperty(String property, Variant value, String pin2);
  public void DisableAll(String password);
  public void DisableAllIncoming(String password);
  public void DisableAllOutgoing(String password);
  public void ChangePassword(String old, String _new);

}
