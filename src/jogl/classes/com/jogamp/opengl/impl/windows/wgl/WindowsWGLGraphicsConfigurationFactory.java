/*
 * Copyright (c) 2008 Sun Microsystems, Inc. All Rights Reserved.
 * Copyright (c) 2010 JogAmp Community. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 */

package com.jogamp.opengl.impl.windows.wgl;

import javax.media.nativewindow.AbstractGraphicsConfiguration;
import javax.media.nativewindow.AbstractGraphicsDevice;
import javax.media.nativewindow.AbstractGraphicsScreen;
import javax.media.nativewindow.CapabilitiesChooser;
import javax.media.nativewindow.DefaultGraphicsScreen;
import javax.media.nativewindow.GraphicsConfigurationFactory;
import javax.media.nativewindow.NativeSurface;
import javax.media.nativewindow.NativeWindowException;
import javax.media.nativewindow.NativeWindowFactory;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLCapabilitiesChooser;
import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.GLException;
import javax.media.opengl.GLProfile;

import com.jogamp.nativewindow.impl.windows.GDI;
import com.jogamp.nativewindow.impl.windows.PIXELFORMATDESCRIPTOR;
import javax.media.nativewindow.CapabilitiesImmutable;
import javax.media.opengl.GLCapabilitiesImmutable;

/** Subclass of GraphicsConfigurationFactory used when non-AWT tookits
    are used on Windows platforms. Toolkits will likely need to delegate
    to this one to change the accepted and returned types of the
    GraphicsDevice and GraphicsConfiguration abstractions. */

public class WindowsWGLGraphicsConfigurationFactory extends GraphicsConfigurationFactory {
    protected static final boolean DEBUG = com.jogamp.opengl.impl.Debug.debug("GraphicsConfiguration");

    public WindowsWGLGraphicsConfigurationFactory() {
        GraphicsConfigurationFactory.registerFactory(javax.media.nativewindow.windows.WindowsGraphicsDevice.class, this);
    }

    protected AbstractGraphicsConfiguration chooseGraphicsConfigurationImpl(
            CapabilitiesImmutable capsChosen, CapabilitiesImmutable capsRequested, CapabilitiesChooser chooser, AbstractGraphicsScreen absScreen) {

        if (! (capsChosen instanceof GLCapabilitiesImmutable) ) {
            throw new IllegalArgumentException("This NativeWindowFactory accepts only GLCapabilities objects - chosen");
        }

        if (! (capsRequested instanceof GLCapabilitiesImmutable) ) {
            throw new IllegalArgumentException("This NativeWindowFactory accepts only GLCapabilities objects - requested");
        }

        return chooseGraphicsConfigurationStatic((GLCapabilitiesImmutable)capsChosen, (GLCapabilitiesImmutable)capsRequested, chooser, absScreen);
    }

    protected static WindowsWGLGraphicsConfiguration createDefaultGraphicsConfiguration(GLCapabilitiesImmutable caps,
                                                                                        AbstractGraphicsScreen absScreen) {
        if(null==absScreen) {
            absScreen = DefaultGraphicsScreen.createDefault(NativeWindowFactory.TYPE_WINDOWS);
        }
        return new WindowsWGLGraphicsConfiguration(absScreen, caps, caps, WindowsWGLGraphicsConfiguration.GLCapabilities2PFD(caps), -1, null);
    }

    protected static WindowsWGLGraphicsConfiguration chooseGraphicsConfigurationStatic(GLCapabilitiesImmutable capsChosen,
                                                                                       GLCapabilitiesImmutable capsReq,
                                                                                       CapabilitiesChooser chooser,
                                                                                       AbstractGraphicsScreen absScreen) {
        if(null==absScreen) {
            absScreen = DefaultGraphicsScreen.createDefault(NativeWindowFactory.TYPE_WINDOWS);
        }

        if(!capsChosen.isOnscreen() && capsChosen.getDoubleBuffered()) {
            // OFFSCREEN !DOUBLE_BUFFER // FIXME DBLBUFOFFSCRN
            GLCapabilities caps2 = (GLCapabilities) capsChosen.cloneMutable();
            caps2.setDoubleBuffered(false);
            capsChosen = caps2;
        }

        return new WindowsWGLGraphicsConfiguration(absScreen, capsChosen, capsReq,
                                                   WindowsWGLGraphicsConfiguration.GLCapabilities2PFD(capsChosen), -1,
                                                   (GLCapabilitiesChooser)chooser);
    }

    protected static void updateGraphicsConfiguration(CapabilitiesChooser chooser,
                                                      GLDrawableFactory _factory, NativeSurface ns) {
        WindowsWGLDrawableFactory factory = (WindowsWGLDrawableFactory) _factory;
        if (ns == null) {
            throw new IllegalArgumentException("NativeSurface is null");
        }

        if (chooser != null &&
            !(chooser instanceof GLCapabilitiesChooser)) {
            throw new IllegalArgumentException("This NativeWindowFactory accepts only GLCapabilitiesChooser objects");
        }

        WindowsWGLGraphicsConfiguration config = (WindowsWGLGraphicsConfiguration) ns.getGraphicsConfiguration().getNativeGraphicsConfiguration();
        AbstractGraphicsDevice device = config.getScreen().getDevice();
        WindowsWGLContext sharedContext = (WindowsWGLContext) factory.getOrCreateSharedContextImpl(device);
        if(null==sharedContext) {
            throw new InternalError("SharedContext is null: "+device);
        }
        boolean choosenBywGLPixelFormat = false;
        GLCapabilitiesImmutable capabilities = (GLCapabilitiesImmutable) config.getChosenCapabilities();
        boolean onscreen = capabilities.isOnscreen();
        boolean usePBuffer = capabilities.isPBuffer();
        GLProfile glProfile = capabilities.getGLProfile();
        long hdc = ns.getSurfaceHandle();

        if(0==hdc) {
          throw new GLException("Error: HDC is null "+toHexString(hdc));
        }
        if(DEBUG) {
          Exception ex = new Exception("Info: WindowsWGLGraphicsConfigurationFactory got HDC "+toHexString(hdc));
          ex.printStackTrace();
        }

        PIXELFORMATDESCRIPTOR pfd = null;
        int pixelFormat = -1; // 1-based pixel format
        boolean pixelFormatSet = false;
        GLCapabilitiesImmutable chosenCaps = null;

        if (onscreen) {
          if ((pixelFormat = GDI.GetPixelFormat(hdc)) != 0) {
            // Pixelformat already set by either
            //  - a previous updateGraphicsConfiguration() call on the same HDC,
            //  - the graphics driver, copying the HDC's pixelformat to the new one,
            //  - or the Java2D/OpenGL pipeline's configuration
            if (DEBUG) {
              System.err.println("!!!! NOTE: pixel format already chosen for HDC: " + toHexString(hdc)+
                                 ", pixelformat "+pixelFormat);
            }
            pixelFormatSet = true;
          }

          GLCapabilitiesImmutable[] availableCaps = null;
          int numFormats = 0;
          pfd = WindowsWGLGraphicsConfiguration.createPixelFormatDescriptor();
          // Produce a recommended pixel format selection for the GLCapabilitiesChooser.
          // Use wglChoosePixelFormatARB if user requested multisampling and if we have it available
          int recommendedPixelFormat = pixelFormat; // 1-based pixel format
          boolean gotAvailableCaps = false;          
          synchronized(sharedContext) {
              sharedContext.makeCurrent();
              try {
                  WGLExt wglExt = sharedContext.getWGLExt();
                  boolean haveWGLChoosePixelFormatARB = false;
                  if (wglExt != null) {
                      haveWGLChoosePixelFormatARB = wglExt.isExtensionAvailable("WGL_ARB_pixel_format");
                      if (haveWGLChoosePixelFormatARB) {
                        if(pixelFormat<=0) {
                          int[]   iattributes = new int  [2*WindowsWGLGraphicsConfiguration.MAX_ATTRIBS];
                          float[] fattributes = new float[1];

                          if(WindowsWGLGraphicsConfiguration.GLCapabilities2AttribList(capabilities,
                                                                                       iattributes,
                                                                                       sharedContext,
                                                                                       false,
                                                                                       null)) {
                            int[] pformats = new int[WindowsWGLGraphicsConfiguration.MAX_PFORMATS];
                            int[] numFormatsTmp = new int[1];
                            if (wglExt.wglChoosePixelFormatARB(hdc,
                                                               iattributes, 0,
                                                               fattributes, 0,
                                                               WindowsWGLGraphicsConfiguration.MAX_PFORMATS,
                                                               pformats, 0,
                                                               numFormatsTmp, 0)) {
                              numFormats = numFormatsTmp[0];
                              if (DEBUG) {
                                System.err.println(getThreadName() + ": NumFormats (wglChoosePixelFormatARB) " + numFormats);
                              }
                              if (recommendedPixelFormat<=0 && numFormats > 0) {
                                recommendedPixelFormat = pformats[0];
                                if (DEBUG) {
                                  System.err.println(getThreadName() + ": Used wglChoosePixelFormatARB to recommend pixel format " + recommendedPixelFormat);
                                }
                              }
                            } else {
                              if (DEBUG) {
                                System.err.println(getThreadName() + ": wglChoosePixelFormatARB failed: " + GDI.GetLastError() );
                                Thread.dumpStack();
                              }
                            }
                            if (DEBUG) {
                              if (recommendedPixelFormat <= 0) {
                                System.err.print(getThreadName() + ": wglChoosePixelFormatARB didn't recommend a pixel format: "+GDI.GetLastError());
                                if (capabilities.getSampleBuffers()) {
                                  System.err.print(" for multisampled GLCapabilities");
                                }
                                System.err.println();
                              }
                            }
                          }
                        }

                        availableCaps = WindowsWGLGraphicsConfiguration.HDC2GLCapabilities(sharedContext, hdc, -1, glProfile, pixelFormatSet, onscreen, usePBuffer);
                        gotAvailableCaps = null!=availableCaps ;
                        choosenBywGLPixelFormat = gotAvailableCaps ;
                      } else if (DEBUG) {
                        System.err.println(getThreadName() + ": wglChoosePixelFormatARB not available");
                      }
                  }
              } finally {
                  sharedContext.release();
              }
          } // synchronized(factory.sharedContext)

          if (!gotAvailableCaps) {
            if (DEBUG) {
              System.err.println(getThreadName() + ": Using ChoosePixelFormat ... (LastError: "+GDI.GetLastError()+")");
            }
            pfd = WindowsWGLGraphicsConfiguration.GLCapabilities2PFD(capabilities);
            recommendedPixelFormat = GDI.ChoosePixelFormat(hdc, pfd);
            if (DEBUG) {
              System.err.println(getThreadName() + ": ChoosePixelFormat(HDC "+toHexString(hdc)+") = " + recommendedPixelFormat + " (LastError: "+GDI.GetLastError()+")");
              System.err.println(getThreadName() + ": Used " + capabilities);
            }

            numFormats = GDI.DescribePixelFormat(hdc, 1, 0, null);
            if (numFormats == 0) {
              throw new GLException("Unable to enumerate pixel formats of window " +
                                    toHexString(hdc) + " for GLCapabilitiesChooser (LastError: "+GDI.GetLastError()+")");
            }
            if (DEBUG) {
                System.err.println(getThreadName() + ": NumFormats (DescribePixelFormat) " + numFormats);
            }

            availableCaps = new GLCapabilitiesImmutable[numFormats];
            for (int i = 0; i < numFormats; i++) {
              if (GDI.DescribePixelFormat(hdc, 1 + i, pfd.size(), pfd) == 0) {
                throw new GLException("Error describing pixel format " + (1 + i) + " of device context");
              }
              availableCaps[i] = WindowsWGLGraphicsConfiguration.PFD2GLCapabilities(glProfile, pfd, onscreen, usePBuffer);
            }
          }

          // NOTE: officially, should make a copy of all of these
          // GLCapabilities to avoid mutation by the end user during the
          // chooseCapabilities call, but for the time being, assume they
          // won't be changed

          if(pixelFormat<=0) {
              if(null!=chooser) {
                  // Supply information to chooser
                  try {
                    pixelFormat = chooser.chooseCapabilities(capabilities, availableCaps, recommendedPixelFormat) + 1;
                  } catch (NativeWindowException e) {
                    if(DEBUG) {
                        e.printStackTrace();
                    }
                    pixelFormat = -1;
                  }
              } else {
                  pixelFormat = recommendedPixelFormat;
              }
              if (pixelFormat <= 0) {
                  // keep on going ..
                  if(DEBUG) {
                      System.err.println("WindowsWGLGraphicsConfigurationFactory.updateGraphicsConfiguration .. unable to choose config, using first");
                  }
                  pixelFormat = 1; // default ..
              } else if ( pixelFormat > numFormats ) {
                  // keep on going ..
                  if(DEBUG) {
                    System.err.println("GLCapabilitiesChooser specified invalid index (expected 1.." + numFormats + ", got "+pixelFormat+")");
                  }
                  pixelFormat = 1; // default ..
              }
          }
          chosenCaps = availableCaps[pixelFormat-1];
          if (DEBUG) {
            System.err.println(getThreadName() + ": Chosen pixel format (" + pixelFormat + "):");
            System.err.println(chosenCaps);
          }
          if (GDI.DescribePixelFormat(hdc, pixelFormat, pfd.size(), pfd) == 0) {
            throw new GLException("Error re-describing the chosen pixel format: " + GDI.GetLastError());
          }
        } else {
          // For now, use ChoosePixelFormat for offscreen surfaces until
          // we figure out how to properly choose an offscreen-
          // compatible pixel format
          pfd = WindowsWGLGraphicsConfiguration.GLCapabilities2PFD(capabilities);
          pixelFormat = GDI.ChoosePixelFormat(hdc, pfd);
        }
        if(!pixelFormatSet) {
            if (!GDI.SetPixelFormat(hdc, pixelFormat, pfd)) {
              long lastError = GDI.GetLastError();
              if (DEBUG) {
                System.err.println(getThreadName() + ": SetPixelFormat failed: current context = " + WGL.wglGetCurrentContext() +
                                   ", current DC = " + WGL.wglGetCurrentDC());
                System.err.println(getThreadName() + ": GetPixelFormat(hdc " + toHexString(hdc) + ") returns " + GDI.GetPixelFormat(hdc));
              }
              throw new GLException("Unable to set pixel format " + pixelFormat + " for device context " + toHexString(hdc) + ": error code " + lastError);
            }
            pixelFormatSet=true;
        }
        // Reuse the previously-constructed GLCapabilities because it
        // turns out that using DescribePixelFormat on some pixel formats
        // (which, for example, support full-scene antialiasing) for some
        // reason return that they are not OpenGL-capable
        if (chosenCaps != null) {
          capabilities = chosenCaps;
        } else {
          capabilities = WindowsWGLGraphicsConfiguration.PFD2GLCapabilities(glProfile, pfd, onscreen, usePBuffer);
        }
        config.setCapsPFD(capabilities, pfd, pixelFormat, choosenBywGLPixelFormat);
    }

    protected static String getThreadName() {
        return Thread.currentThread().getName();
    }

    public static String toHexString(long hex) {
        return "0x" + Long.toHexString(hex);
    }
}

