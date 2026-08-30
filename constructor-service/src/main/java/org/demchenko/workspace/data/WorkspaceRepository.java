package org.demchenko.workspace.data;

import org.demchenko.workspace.model.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {
}
