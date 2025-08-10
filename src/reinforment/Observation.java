package reinforment;

public class Observation {
    private boolean isHeadRoomClear;

    public boolean isHeadRoomClear() {
        return isHeadRoomClear;
    }

    public ObservationGrid getObservationGrid() {
        return observationGrid;
    }

    private ObservationGrid observationGrid;

    Observation(ObservationGrid observationGrid, boolean isHeadRoomClear){
        this.observationGrid = observationGrid;
        this.isHeadRoomClear = isHeadRoomClear;
    }
}
