package org.ofono;
import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
public interface ConnectionManager extends DBusInterface
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
   public static class ContextAdded extends DBusSignal
   {
      public final DBusInterface path;
      public final Map<String,Variant> properties;
      public ContextAdded(String path, DBusInterface path2, Map<String,Variant> properties) throws DBusException
      {
         super(path, path2, properties);
         this.path = path2;
         this.properties = properties;
      }
   }
   public static class ContextRemoved extends DBusSignal
   {
      public final DBusInterface path;
      public ContextRemoved(String path, DBusInterface path2) throws DBusException
      {
         super(path, path2);
         this.path = path2;
      }
   }

  public Map<String,Variant> GetProperties();
  public void SetProperty(String property, Variant value);
  public DBusInterface AddContext(String type);
  public void RemoveContext(DBusInterface path);
  public void DeactivateAll();
  public List<Struct5> GetContexts();
  public void ResetContexts();

}
