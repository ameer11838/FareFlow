package com.fareflow.network;

import jakarta.persistence.*;

@Entity
@Table(name = "transit_lines")
public class TransitLine {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String agency;
    @Column(nullable = false) private String mode;
    @Column(name = "fare_policy", nullable = false) private String farePolicy;
    @Column(name = "headway_minutes", nullable = false) private int headwayMinutes;
    @Column(nullable = false) private boolean active = true;

    protected TransitLine() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getAgency() { return agency; }
    public String getMode() { return mode; }
    public String getFarePolicy() { return farePolicy; }
    /** Average wait, used as a deterministic stand-in for a timetable. */
    public int getHeadwayMinutes() { return headwayMinutes; }
    public boolean isActive() { return active; }
}
