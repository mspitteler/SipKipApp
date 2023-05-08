#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <opus.h>

typedef struct {
    OpusEncoder *encoder;
    int channels;
} opus_t;

JNIEXPORT void JNICALL
Java_com_gmail_spittelermattijn_sipkip_opusjni_Opus_open(JNIEnv *env, jobject thiz, jint channels,
                                                         jint sampleFreq, jint bitrate) {
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jfieldID fid = (*env)->GetFieldID(env, cls, "handle", "J");

    int err = -1;
    opus_t *opus = malloc(sizeof(opus_t));
    opus->encoder = opus_encoder_create(sampleFreq, channels, OPUS_APPLICATION_AUDIO, &err);
    opus->channels = channels;
    (*env)->SetLongField(env, thiz, fid, (jlong) opus);

    if (err < 0) {
        jclass class_rex = (*env)->FindClass(env, "java/lang/RuntimeException");
        (*env)->ThrowNew(env, class_rex, opus_strerror(err));
        return;
    }

    err = opus_encoder_ctl(opus->encoder, OPUS_SET_BITRATE(bitrate));

    if (err < 0) {
        jclass class_rex = (*env)->FindClass(env, "java/lang/RuntimeException");
        (*env)->ThrowNew(env, class_rex, opus_strerror(err));
        return;
    }
}

JNIEXPORT jbyteArray JNICALL
Java_com_gmail_spittelermattijn_sipkip_opusjni_Opus_encode(JNIEnv *env, jobject thiz,
                                                           jshortArray array, jint pos, jint len) {
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jfieldID fid = (*env)->GetFieldID(env, cls, "handle", "J");
    opus_t *opus = (opus_t *)(*env)->GetLongField(env, thiz, fid);

    void *out;
    int out_len;

    out_len = (int)sizeof(short int) * len;
    if (out_len < 4096)
        out_len = 4096;
    out = malloc(out_len);

    jshort *bufferPtr = (*env)->GetShortArrayElements(env, array, NULL);
    int frameSize = len / opus->channels;
    out_len = opus_encode(opus->encoder, bufferPtr + pos, frameSize, out, out_len);
    (*env)->ReleaseShortArrayElements(env, array, bufferPtr, 0);
    
    if (out_len < 0) {
      jclass class_rex = (*env)->FindClass(env, "java/lang/RuntimeException");
      (*env)->ThrowNew(env, class_rex, opus_strerror(out_len));
      free(out);
      return NULL;
    }

    jbyteArray ret = (*env)->NewByteArray(env, out_len);
    (*env)->SetByteArrayRegion(env, ret, 0, out_len, out);
    free(out);
    return ret;
}

JNIEXPORT jbyteArray JNICALL
Java_com_gmail_spittelermattijn_sipkip_opusjni_Opus_encodeFloat(JNIEnv *env, jobject thiz,
                                                                jfloatArray buf, jint pos, jint len) {
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jfieldID fid = (*env)->GetFieldID(env, cls, "handle", "J");
    opus_t *opus = (opus_t *)(*env)->GetLongField(env, thiz, fid);

    void *out;
    int out_len;

    out_len = (int)sizeof(float) * len;
    if (out_len < 4096)
        out_len = 4096;
    out = malloc(out_len);

    jfloat *bufferPtr = (*env)->GetFloatArrayElements(env, buf, NULL);
    int frameSize = len / opus->channels;
    out_len = opus_encode_float(opus->encoder, bufferPtr + pos, frameSize, out, out_len);
    (*env)->ReleaseFloatArrayElements(env, buf, bufferPtr, 0);

    if (out_len < 0) {
        jclass class_rex = (*env)->FindClass(env, "java/lang/RuntimeException");
        (*env)->ThrowNew(env, class_rex, opus_strerror(out_len));
        free(out);
        return NULL;
    }

    jbyteArray ret = (*env)->NewByteArray(env, out_len);
    (*env)->SetByteArrayRegion(env, ret, 0, out_len, out);
    free(out);
    return ret;
}

JNIEXPORT void JNICALL
Java_com_gmail_spittelermattijn_sipkip_opusjni_Opus_close(JNIEnv *env, jobject thiz) {
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jfieldID fid = (*env)->GetFieldID(env, cls, "handle", "J");
    opus_t *opus = (opus_t *)(*env)->GetLongField(env, thiz, fid);

    opus_encoder_destroy(opus->encoder);
    free(opus);

    (*env)->SetLongField(env, thiz, fid, (jlong)0);
}
