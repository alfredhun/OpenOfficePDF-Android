#pragma once

#include <stdint.h>
#include <vector>

namespace cv {

struct Point2f {
    float x;
    float y;
    Point2f() : x(0), y(0) {}
    Point2f(float _x, float _y) : x(_x), y(_y) {}
};

struct Size {
    int width;
    int height;
    Size() : width(0), height(0) {}
    Size(int _w, int _h) : width(_w), height(_h) {}
};

class Mat {
public:
    int rows;
    int cols;
    uint8_t* data;

    Mat() : rows(0), cols(0), data(nullptr) {}
    Mat(int _rows, int _cols, int _type, void* _data = nullptr)
        : rows(_rows), cols(_cols), data(reinterpret_cast<uint8_t*>(_data)) {}
};

Mat getPerspectiveTransform(const Point2f src[], const Point2f dst[]);
void warpPerspective(const Mat& src, Mat& dst, const Mat& M, Size dsize);

} // namespace cv
