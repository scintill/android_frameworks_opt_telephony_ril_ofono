package org.ofono;
import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
public interface VoiceCallManager extends DBusInterface
{
   public static class Forwarded extends DBusSignal
   {
      public final String type;
      public Forwarded(String path, String type) throws DBusException
      {
         super(path, type);
         this.type = type;
      }
   }
   public static class BarringActive extends DBusSignal
   {
      public final String type;
      public BarringActive(String path, String type) throws DBusException
      {
         super(path, type);
         this.type = type;
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
   public static class CallAdded extends DBusSignal
   {
      public final Path path;
      public final Map<String,Variant> properties;
      public CallAdded(String vcmPath, Path path, Map<String,Variant> properties) throws DBusException
      {
         super(vcmPath, path, properties);
         this.path = path;
         this.properties = properties;
      }
   }
   public static class CallRemoved extends DBusSignal
   {
      public final Path path;
      public CallRemoved(String vcmPath, Path path) throws DBusException
      {
         super(vcmPath, path);
         this.path = path;
      }
   }

  public Map<String,Variant> GetProperties();
  public Path Dial(String number, String hide_callerid);
  public void Transfer();
  public void SwapCalls();
  public void ReleaseAndAnswer();
  public void ReleaseAndSwap();
  public void HoldAndAnswer();
  public void HangupAll();
  public List<DBusInterface> PrivateChat(DBusInterface call);
  public List<DBusInterface> CreateMultiparty();
  public void HangupMultiparty();
  public void SendTones(String SendTones);
  public List<Struct1> GetCalls();

}
