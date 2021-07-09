package me.jellysquid.mods.sodium.client.gl.func;

import org.lwjgl.opengl.ARBIndirectParameters;
import org.lwjgl.opengl.GLCapabilities;

public enum GlIndirectMultiDrawIndirectParametersFunctions {
    ARB {
        @Override
        public void glMultiDrawArraysIndirectCount(int mode, long indirect, long drawCount, int maxCount, int stride) {
            ARBIndirectParameters.glMultiDrawArraysIndirectCountARB(mode, indirect, drawCount, maxCount, stride);
        }

        @Override
        public void glMultiDrawElementArraysIndirectCount(int mode, int type, long indirect, long drawCount, int maxCount, int stride) {
            ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(mode, type, indirect, drawCount, maxCount, stride);
        }
    },
    UNSUPPORTED {
        @Override
        public void glMultiDrawArraysIndirectCount(int mode, long indirect, long drawCount, int maxCount, int stride) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void glMultiDrawElementArraysIndirectCount(int mode, int type, long indirect, long drawCount, int maxCount, int stride) {
            throw new UnsupportedOperationException();
        }
    };

    public static GlIndirectMultiDrawIndirectParametersFunctions load(GLCapabilities capabilities) {
        if (capabilities.GL_ARB_multi_draw_indirect && capabilities.GL_ARB_draw_indirect && capabilities.GL_ARB_indirect_parameters) {
            return ARB;
        } else {
            return UNSUPPORTED;
        }
    }

    public abstract void glMultiDrawArraysIndirectCount(int mode, long indirect, long drawCount, int maxCount, int stride);

    public abstract void glMultiDrawElementArraysIndirectCount(int mode, int type, long indirect, long drawCount, int maxCount, int stride);
}
