# Animated park map experiment (Sikka)

This branch is reserved for testing the improved interactive park map together with the frontend branch of the same name in `Abdulaziz-Shafae/Sikka-front`.

## Current backend contract

- `Ride` has `sourceRideId`, `mapX`, `mapY`, `rideFeatures`, `status` and `waitMinutes`.
- `Place` has `sourcePlaceId`, `mapX`, `mapY`, `kind` and `status`.
- The frontend already includes a park graph of nodes, rides, places and walking paths.

The first experiment is frontend-only: animations are illustrative and do not represent actual park ride movement or operating status. There are no JPA model, database schema, authentication, payment, or API changes on this branch.

## Next evaluation

Compare mouse/touch panning, zoom, camera focusing, animation performance, accessible reduced-motion, and UI on mobile and desktop against the existing map before integrating any changes into main.
