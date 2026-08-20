package com.fareflow.network;

import jakarta.persistence.*;

@Entity
@Table(name = "transit_line_stops")
public class TransitLineStop {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "line_id", nullable = false) private Long lineId;
    @Column(name = "stop_id", nullable = false) private Long stopId;
    @Column(nullable = false) private int sequence;
    @Column(name = "minutes_from_start", nullable = false) private int minutesFromStart;

    protected TransitLineStop() {
    }

    public Long getLineId() { return lineId; }
    public Long getStopId() { return stopId; }
    public int getSequence() { return sequence; }
    public int getMinutesFromStart() { return minutesFromStart; }
}
