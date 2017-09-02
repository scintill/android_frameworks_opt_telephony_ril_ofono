/*
 * Copyright 2017 Joey Hewitt <joey@joeyhewitt.com>
 *
 * This file is part of ril_ofono.
 *
 * ril_ofono is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ril_ofono is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ril_ofono.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.scintill.ril_ofono.build;

import android.os.Message;

import com.android.internal.telephony.CommandsInterface;

import net.scintill.ril_ofono.RilOfono;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import libcore.reflect.Types;

public class BuildRilWrapper {

    public void buildToFile(String path) throws IOException, NoSuchMethodException {
        File f = new File(path);
        PrintStream os = new PrintStream(new FileOutputStream(f));
        //PrintStream os = System.err;
        for (Method m : CommandsInterface.class.getMethods()) {
            //boolean needsImpl = Modifier.isAbstract(BaseCommands.class.getMethod(m.getName(), m.getParameterTypes()).getModifiers());
            boolean needsWrappedImpl = true;
            try {
                RilOfono.class.getDeclaredMethod(m.getName(), getParameterTypesExcludingMessage(m));
            } catch (NoSuchMethodException e) {
                needsWrappedImpl = false;
            }
            if (needsWrappedImpl) {
                os.println(getMethodSignature(m).replace("java.lang.String", "String")+" {");
                    String messageParamName = "msg";
                    Class[] paramTypes = m.getParameterTypes();
                    boolean isAsync = Arrays.asList(paramTypes).contains(Message.class);
                    if (isAsync) {
                        os.println("runOnDbusThread(new Runnable() {");
                        os.println("public void run() {");
                        os.println("sCurrentMsg = msg;");
                        os.println("try {");
                        os.print("Object ret = ");
                    } else {
                        os.println("try {");
                        os.println("sCurrentMsg = null;");
                        os.print("return ");
                    }
                    os.print("mRilImpl."+m.getName()+"(");
                    char paramName = 'a';
                    for (Class paramType : paramTypes) {
                        if (!Message.class.isAssignableFrom(paramType)) {
                            if (paramName != 'a') {
                                os.print(", ");
                            }
                            os.print(paramName);
                        }
                        paramName++;
                    }
                    os.println(");");
                    if (isAsync) {
                        os.println("respondOk(\""+m.getName()+"\", "+messageParamName+", ret);");
                    }
                    os.println("} catch (CommandException exc) {");
                    if (isAsync) {
                        os.println("respondExc(\"" + m.getName() + "\", " + messageParamName + ", exc, null);");
                    } else {
                        os.println("// XXX implement me!");
                    }
                    os.println("} catch (Throwable thr) {");
                    os.println("Rlog.e(TAG, \"Uncaught exception in "+m.getName()+"\", privExc(thr));");
                    if (isAsync) {
                        os.println("respondExc(\""+m.getName()+"\", "+messageParamName+", new CommandException(GENERIC_FAILURE), null);");
                    } else {
                        os.println("// XXX implement me!");
                    }
                    os.println("}");
                    if (isAsync) {
                        os.println("}});");
                    }
                os.println("}");
            }
        }
    }

    private static String getMethodSignature(Method method) {
        StringBuilder buf = new StringBuilder();
        buf.append(Modifier.toString(method.getModifiers() & ~Modifier.ABSTRACT));
        buf.append(' ');
        buf.append(method.getReturnType().getName());
        buf.append(' ');
        buf.append(method.getName());
        buf.append('(');
        char paramName = 'a';
        for (Class paramClass : method.getParameterTypes()) {
            if (paramName != 'a') {
                buf.append(", ");
            }
            buf.append("final ");
            Types.appendTypeName(buf, paramClass);
            buf.append(' ');
            if (!Message.class.isAssignableFrom(paramClass)) {
                buf.append(paramName);
                paramName++;
            } else {
                buf.append("msg");
            }
        }
        buf.append(')');
        return buf.toString().replaceAll("java.lang.String", "String");
    }

    private Class[] getParameterTypesExcludingMessage(Method method) {
        List<Class> list = new ArrayList<>();
        for (Class paramClass : method.getParameterTypes()) {
            if (!Message.class.isAssignableFrom(paramClass)) list.add(paramClass);
        }
        return list.toArray(new Class[list.size()]);
    }

}
