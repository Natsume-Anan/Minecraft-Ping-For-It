package nx.pingwheel.common.client;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import nx.pingwheel.common.client.marker.ClientMarkerStore;
import nx.pingwheel.common.domain.MarkerId;
import nx.pingwheel.common.domain.PingType;
import nx.pingwheel.common.domain.PingTypeCatalog;
import nx.pingwheel.common.domain.Target;
import nx.pingwheel.common.interaction.state.PingInteractionPhase;
import nx.pingwheel.common.interaction.wheel.WheelSelection;
import nx.pingwheel.common.marker.MarkerAnchor;
import nx.pingwheel.common.marker.MarkerSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPingRuntimeTest {

	@Test
	void sameIdLocatorUpsertIsNotASecondSoundOrChatReceipt() {
		ClientMarkerStore store = new ClientMarkerStore(10L);
		MarkerId markerId = new MarkerId(7L);
		Target.ExternalBlockTarget first = Target.ExternalBlockTarget.committed(
			"minecraft:overworld", "sable", "tracking-id", "minecraft:chest", "locator-a", true);
		Target.ExternalBlockTarget updated = Target.ExternalBlockTarget.committed(
			"minecraft:overworld", "sable", "tracking-id", "minecraft:chest", "locator-b", true);

		assertTrue(ClientPingRuntime.isNewMarkerReceipt(store, markerId));
		store.onCreated(snapshot(markerId, first), 0L);

		assertFalse(ClientPingRuntime.isNewMarkerReceipt(store, markerId));
		store.onCreated(snapshot(markerId, updated), 1L);
		assertFalse(ClientPingRuntime.isNewMarkerReceipt(store, markerId));
	}

	@Test
	void staleReleaseReusesLastHoveredSector() {
		WheelSelection sector = WheelSelection.sector(pingType("attention"));

		WheelSelection resolved = ClientPingRuntime.resolveWheelReleaseSelection(
			false, PingInteractionPhase.WHEEL_OPEN, false, WheelSelection.NONE, sector);

		assertEquals(sector, resolved);
	}

	@Test
	void staleReleaseReusesCenter() {
		WheelSelection resolved = ClientPingRuntime.resolveWheelReleaseSelection(
			false, PingInteractionPhase.WHEEL_OPEN, false, WheelSelection.NONE, WheelSelection.CENTER);

		assertSame(WheelSelection.CENTER, resolved);
	}

	@Test
	void staleReleaseWithoutLastSelectionStaysNone() {
		WheelSelection resolved = ClientPingRuntime.resolveWheelReleaseSelection(
			false, PingInteractionPhase.WHEEL_OPEN, false, WheelSelection.NONE, WheelSelection.NONE);

		assertSame(WheelSelection.NONE, resolved);
	}

	@Test
	void freshReleaseKeepsConsumedSectorAndIgnoresLast() {
		WheelSelection sector = WheelSelection.sector(pingType("attention"));

		WheelSelection resolved = ClientPingRuntime.resolveWheelReleaseSelection(
			false, PingInteractionPhase.WHEEL_OPEN, true, sector, WheelSelection.CENTER);

		assertEquals(sector, resolved);
	}

	@Test
	void freshOutsideRingReleaseDoesNotFallback() {
		WheelSelection sector = WheelSelection.sector(pingType("attention"));

		WheelSelection resolved = ClientPingRuntime.resolveWheelReleaseSelection(
			false, PingInteractionPhase.WHEEL_OPEN, true, WheelSelection.NONE, sector);

		assertSame(WheelSelection.NONE, resolved);
	}

	@Test
	void holdDoesNotUseReleaseFallback() {
		WheelSelection sector = WheelSelection.sector(pingType("attention"));

		WheelSelection resolved = ClientPingRuntime.resolveWheelReleaseSelection(
			true, PingInteractionPhase.WHEEL_OPEN, false, WheelSelection.NONE, sector);

		assertSame(WheelSelection.NONE, resolved);
	}

	@Test
	void nonWheelPhaseDoesNotUseReleaseFallback() {
		WheelSelection sector = WheelSelection.sector(pingType("attention"));

		WheelSelection resolved = ClientPingRuntime.resolveWheelReleaseSelection(
			false, PingInteractionPhase.PRESSED, false, WheelSelection.NONE, sector);

		assertSame(WheelSelection.NONE, resolved);
	}

	private static PingType pingType(String id) {
		return PingTypeCatalog.builtIn().findById(id).orElseThrow();
	}

	private static MarkerSnapshot snapshot(MarkerId id, Target target) {
		return new MarkerSnapshot(
			id,
			new UUID(0L, 1L),
			target,
			"entity_block",
			"attention",
			new MarkerAnchor(1.0, 2.0, 3.0),
			1L,
			20L);
	}
}
