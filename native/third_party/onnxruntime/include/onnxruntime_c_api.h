#pragma once

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct OrtEnv OrtEnv;
typedef struct OrtSession OrtSession;
typedef struct OrtSessionOptions OrtSessionOptions;
typedef struct OrtMemoryInfo OrtMemoryInfo;
typedef struct OrtValue OrtValue;
typedef struct OrtAllocator OrtAllocator;

typedef enum ONNXTensorElementDataType {
    ONNX_TENSOR_ELEMENT_DATA_TYPE_UNDEFINED = 0,
    ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT = 1,
    ONNX_TENSOR_ELEMENT_DATA_TYPE_UINT8 = 2,
    ONNX_TENSOR_ELEMENT_DATA_TYPE_INT8 = 3,
    ONNX_TENSOR_ELEMENT_DATA_TYPE_INT32 = 6,
    ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64 = 7
} ONNXTensorElementDataType;

typedef struct OrtApi {
    int (*CreateEnv)(int log_severity_level, const char* logid, OrtEnv** out);
    void (*ReleaseEnv)(OrtEnv* env);
    int (*CreateSessionOptions)(OrtSessionOptions** options);
    void (*ReleaseSessionOptions)(OrtSessionOptions* options);
    int (*SetIntraOpNumThreads)(OrtSessionOptions* options, int intra_op_num_threads);
    int (*CreateSession)(const OrtEnv* env, const char* model_path, const OrtSessionOptions* options, OrtSession** out);
    void (*ReleaseSession)(OrtSession* session);
} OrtApi;

typedef struct OrtApiBase {
    const OrtApi* (*GetApi)(uint32_t version);
} OrtApiBase;

const OrtApiBase* OrtGetApiBase(void);

#ifdef __cplusplus
}
#endif
