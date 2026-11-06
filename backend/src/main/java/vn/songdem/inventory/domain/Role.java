package vn.songdem.inventory.domain;

public enum Role {
    MANAGER("Quản lý"),
    STAFF("Nhân viên"),
    ACCOUNTANT("Kế toán");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
