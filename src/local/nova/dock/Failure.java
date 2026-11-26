package local.nova.dock;

/** Stable error codes are stored, and translated only when presented. */
final class Failure extends RuntimeException {
    final String code, detail;
    Failure(String code) { this(code,""); }
    Failure(String code,String detail) { super(code); this.code=code; this.detail=detail==null?"":detail; }
}
