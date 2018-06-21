# Sound Map

## Description
Mobile application that uses audio sampling to perform acoustic mapping.

## TODO
- (Maybe unnecessary) Throw out single samples if they are too far from their neighbors
- Add support for Server contact:
  * Login Interface
  * Receive Markers
  * Transmit Data samples with the below proposed interface:

## Proposed Sample Packet (JSON)
```
{
  "user":<user>,
  "avg_lat":<average_lattitude>,
  "avg_lng":<average_longitude>,
  "avg_vol":<average_audio_intensity>
}
```

## License
TODO
