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

package net.scintill.ril_ofono;

import android.os.Message;

import com.android.internal.telephony.CommandsInterface;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/*package*/ class BuildRilWrapper {

    public static void main(String[] args) {
        try {
            build(System.out);
        } catch (Exception t) {
            throw new RuntimeException(t);
        }
    }

    private static void build(PrintStream os) throws IOException, NoSuchMethodException, ClassNotFoundException {
        os.println("package net.scintill.ril_ofono;");

        os.println("import android.telephony.Rlog;");
        os.println("import com.android.internal.telephony.CommandException;");
        os.println("import static net.scintill.ril_ofono.RilOfono.respondExc;");
        os.println("import static net.scintill.ril_ofono.RilOfono.runOnDbusThread;");
        os.println("import static com.android.internal.telephony.CommandException.Error.GENERIC_FAILURE;");

        os.println("public class RilWrapper extends RilWrapperBase {");

        os.println("public RilWrapper(android.content.Context ctx, int networkMode, int cdmaSubscription, Integer instanceId) {");
        os.println("super(ctx);");
        os.println("}");

        List<Method> commandsIfaceMethods = Arrays.asList(CommandsInterface.class.getMethods());
        Collections.sort(commandsIfaceMethods, new Comparator<Method>() {
            @Override
            public int compare(Method method, Method t1) {
                return method.getName().compareTo(t1.getName());
            }
        }); // for comparing with old manually autogenned code

        for (Method commandsIfaceMethod : commandsIfaceMethods) {
            Class<?>[] paramTypesExcludingMessage = getParameterTypesExcludingMessage(commandsIfaceMethod).toArray(new Class<?>[0]);
            Class<?> moduleClass = findModuleClass(commandsIfaceMethod, paramTypesExcludingMessage);
            if (moduleClass == null) {
                continue; // <---
            }
            Method moduleMethod = moduleClass.getMethod(commandsIfaceMethod.getName(), paramTypesExcludingMessage);

            os.println(getMethodSignature(commandsIfaceMethod) + " {");
            String messageParamName = "msg";
            Class<?>[] paramTypes = commandsIfaceMethod.getParameterTypes();
            //os.printf("Rlog.v(TAG, \"%s \" + (%s != null ? %s.what : null) + \" \" + (%s != null ? %s.getTarget() : \"\"));%n", commandsIfaceMethod.getName(), messageParamName, messageParamName, messageParamName, messageParamName);
            boolean isAsync = paramTypesExcludingMessage.length != paramTypes.length;
            boolean isOkOnMainThread = moduleMethod.isAnnotationPresent(OkOnMainThread.class);
            if (!isAsync) {
                throw new RuntimeException(moduleMethod+" is not async! generation of synchronous methods not implemented");
            }

            String moduleVarName = "mRilOfono.m"+(moduleClass != RilOfono.class ? moduleClass.getSimpleName() : "MiscModule");

            os.printf("if (%s == null) { respondExc(\"%s [nomodule]\", %s, new CommandException(GENERIC_FAILURE), null); return; }%n",
                    moduleVarName,
                    commandsIfaceMethod.getName(), messageParamName);

            if (!isOkOnMainThread) {
                os.println("runOnDbusThread(new Runnable() {");
                os.println("public void run() {");
            }
            os.println("sCurrentMsg = msg;");
            os.println("try {");
            os.print("Object ret = ");
            os.print(moduleVarName+"." + commandsIfaceMethod.getName() + "(");
            char paramName = 'a';
            for (Class<?> paramType : paramTypes) {
                if (!Message.class.isAssignableFrom(paramType)) {
                    if (paramName != 'a') {
                        os.print(", ");
                    }
                    os.print(paramName);
                }
                paramName++;
            }
            os.println(");");
            os.println("respondOk(\"" + commandsIfaceMethod.getName() + "\", " + messageParamName + ", ret);");
            os.println("} catch (CommandException exc) {");
            os.println("respondExc(\"" + commandsIfaceMethod.getName() + "\", " + messageParamName + ", exc, null);");
            os.println("} catch (Throwable thr) {");
            os.printf("RilOfono.logUncaughtException(\"%s\", thr);%n", commandsIfaceMethod.getName());
            os.println("respondExc(\"" + commandsIfaceMethod.getName() + "\", " + messageParamName + ", new CommandException(GENERIC_FAILURE), null);");
            os.println("}");
            if (!isOkOnMainThread) {
                os.println("}");
                os.println("});");
            }
            os.println("}");
            os.println();
        }
        os.println("}");
        os.close();
    }

    private static String getMethodSignature(Method commandsIfaceMethod) {
        StringBuilder buf = new StringBuilder();

        buf.append("@Deprecated\n"); // maybe not really, but this is the cleanest way to suppress a few warnings.
        // There is one CommandsInterface method with @deprecated doc flag, which is hard for us to detect,
        // and causes javac to warn because we didn't put @Deprecated annotation on the implementing method.
        // So, just mark them all as deprecated. No sentient beings will be seeing the code we're generating.

        buf.append(Modifier.toString(commandsIfaceMethod.getModifiers() & ~Modifier.ABSTRACT));
        buf.append(' ');
        buf.append(commandsIfaceMethod.getReturnType().getName());
        buf.append(' ');
        buf.append(commandsIfaceMethod.getName());
        buf.append('(');
        char paramName = 'a';
        for (Class<?> paramClass : commandsIfaceMethod.getParameterTypes()) {
            if (paramName != 'a') {
                buf.append(", ");
            }
            buf.append("final ");
            AospUtils.appendTypeName(buf, paramClass);
            buf.append(' ');
            if (!Message.class.isAssignableFrom(paramClass)) {
                buf.append(paramName);
                paramName++;
            } else {
                buf.append("msg");
            }
        }
        buf.append(')');
        return buf.toString();
    }

    private static List<Class<?>> getParameterTypesExcludingMessage(Method method) {
        List<Class<?>> list = new ArrayList<>();
        for (Class<?> paramClass : method.getParameterTypes()) {
            if (!Message.class.isAssignableFrom(paramClass)) list.add(paramClass);
        }
        return list;
    }

    private static final Class<?>[] moduleClasses = new Class<?>[] {
            ModemModule.class, NetworkRegistrationModule.class, SmsModule.class, SimModule.class,
            VoicecallModule.class, DatacallModule.class,
            SupplementaryServicesModule.class,
            RilOfono.class,
    };

    private static Class<?> findModuleClass(Method commandsIfaceMethod, Class<?>[] paramTypesExcludingMessage) {
        for (Class<?> moduleClass : moduleClasses) {
            try {
                moduleClass.getMethod(commandsIfaceMethod.getName(), paramTypesExcludingMessage);
            } catch (NoSuchMethodException e) {
                continue; // <--
            }
            return moduleClass;
        }
        return null;
    }

}
