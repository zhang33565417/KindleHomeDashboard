-- PW4 X11 surface. FBInk only submits the resulting pixels to the panel.
local ffi = require("ffi")
ffi.cdef[[
typedef struct _XDisplay Display;
typedef struct _XVisual Visual;
typedef struct _XImage XImage;
typedef struct _XGC *GC;
typedef unsigned long Window;
typedef struct {
  unsigned long background_pixmap, background_pixel, border_pixmap, border_pixel;
  int bit_gravity, win_gravity, backing_store;
  unsigned long backing_planes, backing_pixel;
  int save_under;
  long event_mask, do_not_propagate_mask;
  int override_redirect;
  unsigned long colormap, cursor;
} XSetWindowAttributes;
typedef union { int type; long pad[24]; } XEvent;
typedef struct { int fd; short events, revents; } pollfd;
int poll(pollfd *, unsigned long, int);
void *malloc(size_t);
void free(void *);
Display *XOpenDisplay(const char *);
Window XDefaultRootWindow(Display *);
int XDefaultScreen(Display *);
int XDefaultDepth(Display *, int);
Visual *XDefaultVisual(Display *, int);
int XDisplayWidth(Display *, int);
int XDisplayHeight(Display *, int);
Window XCreateSimpleWindow(Display *, Window, int, int, unsigned, unsigned, unsigned, unsigned long, unsigned long);
int XChangeWindowAttributes(Display *, Window, unsigned long, XSetWindowAttributes *);
int XStoreName(Display *, Window, const char *);
int XSelectInput(Display *, Window, long);
int XMapRaised(Display *, Window);
GC XCreateGC(Display *, Window, unsigned long, void *);
XImage *XCreateImage(Display *, Visual *, unsigned, int, int, char *, unsigned, unsigned, int, int);
int XPutImage(Display *, Window, GC, XImage *, int, int, int, int, unsigned, unsigned);
int XSync(Display *, int);
int XPending(Display *);
int XNextEvent(Display *, XEvent *);
int XConnectionNumber(Display *);
void *gdk_pixbuf_new_from_file(const char *, void *);
int gdk_pixbuf_get_width(const void *);
int gdk_pixbuf_get_height(const void *);
int gdk_pixbuf_get_n_channels(const void *);
int gdk_pixbuf_get_rowstride(const void *);
unsigned char *gdk_pixbuf_get_pixels(const void *);
void g_object_unref(void *);
void g_type_init(void);
]]
io.stdout:setvbuf("line")
local x = ffi.load("libX11.so.6")
local g = ffi.load("libgdk_pixbuf-2.0.so.0")
local obj = ffi.load("libgobject-2.0.so.0")
obj.g_type_init()
local d = x.XOpenDisplay(":0")
assert(d ~= nil, "Cannot open X11")
local s = x.XDefaultScreen(d)
local w, h = x.XDisplayWidth(d, s), x.XDisplayHeight(d, s)
assert(w == 1072 and h == 1448 and x.XDefaultDepth(d, s) == 8, "Unsupported display")
local win = x.XCreateSimpleWindow(d, x.XDefaultRootWindow(d), 0, 0, w, h, 0, 0, 255)
local attrs = ffi.new("XSetWindowAttributes[1]")
attrs[0].override_redirect = 1
x.XChangeWindowAttributes(d, win, 512, attrs)
x.XStoreName(d, win, "Kindle Home Dashboard")
x.XSelectInput(d, win, 32768)
local gc = x.XCreateGC(d, win, 0, nil)
assert(gc ~= nil, "Cannot create X11 graphics context")
local pixels = ffi.cast("uint8_t *", ffi.C.malloc(w*h))
assert(pixels ~= nil, "Cannot allocate dashboard frame")
local img = x.XCreateImage(d, x.XDefaultVisual(d,s), 8, 2, 0,
    ffi.cast("char *", pixels), w, h, 32, w)
assert(img ~= nil, "Cannot create X11 image")
ffi.fill(pixels, w*h, 255)
local run = assert(arg[1])
local fb = assert(arg[2])
assert(fb:match("^[/a-zA-Z0-9._-]+$"))
local painted = false
local function refresh(left, top, width, height)
    x.XPutImage(d, win, gc, img, left, top, left, top, width, height)
    x.XSync(d, 0)
    return os.execute(string.format("%s -q -w -s top=%d,left=%d,width=%d,height=%d",
        fb, top, left, width, height)) == 0
end
local function rect(left, top, width, height)
    assert(left and top and width and height and left >= 0 and top >= 0
        and width > 0 and height > 0 and left+width <= w and top+height <= h, "Invalid rectangle")
end
local function layer(path, left, top, width, height)
    rect(left, top, width, height)
    local p = g.gdk_pixbuf_new_from_file(path, nil)
    assert(p ~= nil, "Cannot decode " .. path)
    local sw, sh = g.gdk_pixbuf_get_width(p), g.gdk_pixbuf_get_height(p)
    local n, stride = g.gdk_pixbuf_get_n_channels(p), g.gdk_pixbuf_get_rowstride(p)
    if n ~= 3 and n ~= 4 then obj.g_object_unref(p); error("Unsupported PNG format") end
    local source = g.gdk_pixbuf_get_pixels(p)
    local floor = math.floor
    for j = 0, height-1 do
        local row, out = floor(j*sh/height)*stride, (top+j)*w+left
        for i = 0, width-1 do
            local a = row+floor(i*sw/width)*n
            local grey = floor((source[a]*77+source[a+1]*150+source[a+2]*29+128)/256)
            if n == 4 then grey = floor((grey*source[a+3]+pixels[out+i]*(255-source[a+3])+127)/255) end
            pixels[out+i] = grey
        end
    end
    obj.g_object_unref(p)
end
local function request(f)
    local token, area = f:read("*l"), f:read("*l")
    if not token or not token:match("^%d+$") or not area then return end
    local ok, err = pcall(function()
        local a,b,c,e = area:match("^(%d+) (%d+) (%d+) (%d+)$")
        local left, top, width, height = tonumber(a), tonumber(b), tonumber(c), tonumber(e)
        rect(left, top, width, height)
        for line in f:lines() do
            local lx,ty,ww,hh,path = line:match("^(%d+) (%d+) (%d+) (%d+) (.+)$")
            assert(path, "Invalid layer")
            layer(path, tonumber(lx), tonumber(ty), tonumber(ww), tonumber(hh))
        end
        x.XMapRaised(d, win)
        assert(refresh(left, top, width, height), "Panel refresh failed")
        painted = true
    end)
    if not ok then print(err) end
    local ack = assert(io.open(run.."/window-ack.tmp", "w"))
    ack:write(token, ok and " ok\n" or " error\n")
    ack:close()
    assert(os.rename(run.."/window-ack.tmp", run.."/window-ack"))
end
local ev = ffi.new("XEvent[1]")
local pollfd = ffi.new("pollfd[1]")
pollfd[0].fd, pollfd[0].events = x.XConnectionNumber(d), 1
while true do
    while x.XPending(d) > 0 do
        x.XNextEvent(d, ev)
        if ev[0].type == 12 and painted then refresh(0, 0, w, h) end
    end
    local f = io.open(run.."/window-request", "r")
    if f then
        os.remove(run.."/window-request")
        request(f)
        f:close()
    end
    ffi.C.poll(pollfd, 1, 150)
end
