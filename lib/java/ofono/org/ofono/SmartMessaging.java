package org.ofono;
import java.util.List;
import org.freedesktop.dbus.DBusInterface;
public interface SmartMessaging extends DBusInterface
{

  public void RegisterAgent(DBusInterface path);
  public void UnregisterAgent(DBusInterface path);
  public DBusInterface SendBusinessCard(String to, List<Byte> card);
  public DBusInterface SendAppointment(String to, List<Byte> appointment);

}
