package org.ofono;
import java.util.Map;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
public interface SupplementaryServices extends DBusInterface
{
   public static class NotificationReceived extends DBusSignal
   {
      public final String message;
      public NotificationReceived(String path, String message) throws DBusException
      {
         super(path, message);
         this.message = message;
      }
   }
   public static class RequestReceived extends DBusSignal
   {
      public final String message;
      public RequestReceived(String path, String message) throws DBusException
      {
         super(path, message);
         this.message = message;
      }
   }
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

  public Pair<String, Variant<?>> Initiate(String command);
  public String Respond(String reply);
  public void Cancel();
  public Map<String,Variant> GetProperties();

}
