package com.fareflow.network;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "transit_stops")
public class TransitStop {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String locality;
    @Column(nullable = false) private BigDecimal latitude;
    @Column(nullable = false) private BigDecimal longitude;

    protected TransitStop() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getLocality() { return locality; }
    public double getLatitude() { return latitude.doubleValue(); }
    public double getLongitude() { return longitude.doubleValue(); }
}
