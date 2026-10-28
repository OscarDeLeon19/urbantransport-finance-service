package com.urbantransport.finance.client;

import java.util.UUID;

public interface FleetClient {

    FleetBusResponse getBus(UUID busId);
}
