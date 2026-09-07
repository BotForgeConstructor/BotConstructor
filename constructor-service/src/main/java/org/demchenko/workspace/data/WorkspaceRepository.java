package org.demchenko.workspace.data;

import org.demchenko.workspace.model.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {
    Optional<WorkspaceEntity> findByOwnerUserIdAndDefaultWorkspaceTrue(UUID ownerUserId);

    @Query("""
            select workspace from WorkspaceEntity workspace
            where workspace.id = :workspaceId and exists (
                select membership.id from MembershipEntity membership
                where membership.workspaceId = workspace.id and membership.userId = :userId
            )
            """)
    Optional<WorkspaceEntity> findAccessibleByIdAndUserId(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId
    );
}
