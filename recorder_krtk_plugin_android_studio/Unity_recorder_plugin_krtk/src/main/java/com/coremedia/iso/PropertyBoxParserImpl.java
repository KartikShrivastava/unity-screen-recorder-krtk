package com.coremedia.iso;

import com.coremedia.iso.boxes.Box;
import com.unity3d.player.UnityPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertyBoxParserImpl extends AbstractBoxParser {
    Properties mapping;
    Pattern constuctorPattern = Pattern.compile("(.*)\\((.*?)\\)");
    StringBuilder buildLookupStrings = new StringBuilder();
    ThreadLocal<String> clazzName = new ThreadLocal<>();
    ThreadLocal<String[]> param = new ThreadLocal<>();
    static String[] EMPTY_STRING_ARRAY = new String[0];

    public PropertyBoxParserImpl(String... customProperties) {
        InputStream is = null;
        try {
            is = UnityPlayer.currentActivity.getApplicationContext().getAssets().open("isoparser-default.properties");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            this.mapping = new Properties();

            try {
                this.mapping.load(is);
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null) {
                    cl = ClassLoader.getSystemClassLoader();
                }

                Enumeration<URL> enumeration = cl.getResources("isoparser-custom.properties");

                while(enumeration.hasMoreElements()) {
                    URL url = enumeration.nextElement();
                    InputStream customIS = url.openStream();

                    try {
                        this.mapping.load(customIS);
                    } finally {
                        customIS.close();
                    }
                }

                String[] var8 = customProperties;
                int var7 = customProperties.length;

                for(int var25 = 0; var25 < var7; ++var25) {
                    String customProperty = var8[var25];
                    this.mapping.load(this.getClass().getResourceAsStream(customProperty));
                }
            } catch (IOException var22) {
                throw new RuntimeException(var22);
            }
        } finally {
            try {
                is.close();
            } catch (IOException var20) {
                var20.printStackTrace();
            }

        }

    }

    public PropertyBoxParserImpl(Properties mapping) {
        this.mapping = mapping;
    }

    public Box createBox(String type, byte[] userType, String parent) {
        this.invoke(type, userType, parent);
        String[] param = (String[])this.param.get();

        try {
            Class<Box> clazz = (Class<Box>) Class.forName((String)this.clazzName.get());
            //Class<Box> clazz = (Class<Box>) Class.forName("com.coremedia.iso.boxes.Box");
            if (param.length > 0) {
                Class[] constructorArgsClazz = new Class[param.length];
                Object[] constructorArgs = new Object[param.length];

                for(int i = 0; i < param.length; ++i) {
                    if ("userType".equals(param[i])) {
                        constructorArgs[i] = userType;
                        constructorArgsClazz[i] = byte[].class;
                    } else if ("type".equals(param[i])) {
                        constructorArgs[i] = type;
                        constructorArgsClazz[i] = String.class;
                    } else {
                        if (!"parent".equals(param[i])) {
                            throw new InternalError("No such param: " + param[i]);
                        }

                        constructorArgs[i] = parent;
                        constructorArgsClazz[i] = String.class;
                    }
                }

                Constructor<Box> constructorObject = clazz.getConstructor(constructorArgsClazz);
                return (Box)constructorObject.newInstance(constructorArgs);
            } else {
                return (Box)clazz.newInstance();
            }
        } catch (ClassNotFoundException var9) {
            throw new RuntimeException(var9);
        } catch (InstantiationException var10) {
            throw new RuntimeException(var10);
        } catch (IllegalAccessException var11) {
            throw new RuntimeException(var11);
        } catch (InvocationTargetException var12) {
            throw new RuntimeException(var12);
        } catch (NoSuchMethodException var13) {
            throw new RuntimeException(var13);
        }
    }

    public void invoke(String type, byte[] userType, String parent) {
        String constructor;
        if (userType != null) {
            if (!"uuid".equals(type)) {
                throw new RuntimeException("we have a userType but no uuid box type. Something's wrong");
            }

            constructor = this.mapping.getProperty("uuid[" + Hex.encodeHex(userType).toUpperCase() + "]");
            if (constructor == null) {
                constructor = this.mapping.getProperty(parent + "-uuid[" + Hex.encodeHex(userType).toUpperCase() + "]");
            }

            if (constructor == null) {
                constructor = this.mapping.getProperty("uuid");
            }
        } else {
            constructor = this.mapping.getProperty(type);
            if (constructor == null) {
                String lookup = this.buildLookupStrings.append(parent).append('-').append(type).toString();
                this.buildLookupStrings.setLength(0);
                constructor = this.mapping.getProperty(lookup);
            }
        }

        if (constructor == null) {
            constructor = this.mapping.getProperty("default");
        }

        if (constructor == null) {
            throw new RuntimeException("No box object found for " + type);
        } else {
            if (!constructor.endsWith(")")) {
                this.param.set(EMPTY_STRING_ARRAY);
                this.clazzName.set(constructor);
            } else {
                Matcher m = this.constuctorPattern.matcher(constructor);
                boolean matches = m.matches();
                if (!matches) {
                    throw new RuntimeException("Cannot work with that constructor: " + constructor);
                }

                this.clazzName.set(m.group(1));
                if (m.group(2).length() == 0) {
                    this.param.set(EMPTY_STRING_ARRAY);
                } else {
                    this.param.set(m.group(2).length() > 0 ? m.group(2).split(",") : new String[0]);
                }
            }

        }
    }
}
