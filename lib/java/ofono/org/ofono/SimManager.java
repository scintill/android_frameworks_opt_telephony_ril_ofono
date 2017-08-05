package org.ofono;
import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
public interface SimManager extends DBusInterface
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
  public void SetProperty(String property, Variant value);
  public void ChangePin(String type, String oldpin, String newpin);
  public void EnterPin(String type, String pin);
  public void ResetPin(String type, String puk, String newpin);
  public void LockPin(String type, String pin);
  public void UnlockPin(String type, String pin);
  public List<Byte> GetIcon(byte id);

}
