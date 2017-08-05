package org.ofono;
import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
public interface MessageManager extends DBusInterface
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
   public static class IncomingMessage extends DBusSignal
   {
      public final String message;
      public final Map<String,Variant> info;
      public IncomingMessage(String path, String message, Map<String,Variant> info) throws DBusException
      {
         super(path, message, info);
         this.message = message;
         this.info = info;
      }
   }
   public static class ImmediateMessage extends DBusSignal
   {
      public final String message;
      public final Map<String,Variant> info;
      public ImmediateMessage(String path, String message, Map<String,Variant> info) throws DBusException
      {
         super(path, message, info);
         this.message = message;
         this.info = info;
      }
   }
   public static class MessageAdded extends DBusSignal
   {
      public final DBusInterface path;
      public final Map<String,Variant> properties;
      public MessageAdded(String path, DBusInterface path2, Map<String,Variant> properties) throws DBusException
      {
         super(path, path2, properties);
         this.path = path2;
         this.properties = properties;
      }
   }
   public static class MessageRemoved extends DBusSignal
   {
      public final DBusInterface path;
      public MessageRemoved(String path, DBusInterface path2) throws DBusException
      {
         super(path, path2);
         this.path = path2;
      }
   }

  public Map<String,Variant> GetProperties();
  public void SetProperty(String property, Variant value);
  public DBusInterface SendMessage(String to, String text);
  public List<Struct2> GetMessages();

}
