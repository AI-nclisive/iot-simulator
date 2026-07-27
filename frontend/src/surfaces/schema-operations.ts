import { type NodeDto, canHaveChildren } from "./data-source-schema-editor";

export function collectSubtreeIds(nodes: NodeDto[], rootId: string): Set<string> {
  const visited = new Set<string>();
  const stack: string[] = [rootId];
  while (stack.length > 0) {
    const id = stack.pop()!;
    if (visited.has(id)) continue;
    visited.add(id);
    const children = nodes.filter((n) => n.parentId === id);
    stack.push(...children.map((c) => c.nodeId));
  }
  return visited;
}

export function deleteNodeOperation(nodes: NodeDto[], nodeId: string): NodeDto[] {
  const node = nodes.find((n) => n.nodeId === nodeId);
  if (!node) return nodes;
  const subIds = collectSubtreeIds(nodes, nodeId);
  return nodes
    .filter((n) => !subIds.has(n.nodeId))
    .map((n) => (subIds.has(n.parentId || "") ? { ...n, parentId: null } : n));
}

function newNodeId(): string {
  return `node_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

function cloneSubtree(
  nodes: NodeDto[],
  nodeId: string,
  newParentId: string | null,
  newParentPath: string,
  idMap: Map<string, string>
): NodeDto[] {
  const node = nodes.find((n) => n.nodeId === nodeId);
  if (!node) return [];

  const newId = idMap.get(nodeId) || newNodeId();
  idMap.set(nodeId, newId);

  const newPath = newParentPath ? `${newParentPath}/${node.name}` : `/${node.name}`;
  const cloned: NodeDto = {
    ...node,
    nodeId: newId,
    parentId: newParentId,
    path: newPath,
  };

  const children = nodes.filter((n) => n.parentId === nodeId);
  const clonedChildren = children.flatMap((child) =>
    cloneSubtree(nodes, child.nodeId, newId, newPath, idMap)
  );

  return [cloned, ...clonedChildren];
}

export function duplicateNodeOperation(nodes: NodeDto[], nodeId: string): NodeDto[] {
  const node = nodes.find((n) => n.nodeId === nodeId);
  if (!node) return nodes;

  const idMap = new Map<string, string>();
  const newName = `${node.name} (copy)`;
  const newParentId = node.parentId;
  const newParentPath = newParentId ? (nodes.find((n) => n.nodeId === newParentId)?.path ?? "/") : "";
  const newRootPath = newParentPath ? `${newParentPath}/${newName}` : `/${newName}`;

  const cloned = cloneSubtree(nodes, nodeId, newParentId, newRootPath, idMap);
  const renamed = cloned[0] ? { ...cloned[0], name: newName, path: newRootPath } : null;

  return [...nodes, ...(renamed ? [renamed, ...cloned.slice(1)] : cloned)];
}

export function cutNodeOperation(nodeId: string): { mode: "cut"; nodeId: string } {
  return { mode: "cut", nodeId };
}

export function copyNodeOperation(nodeId: string): { mode: "copy"; nodeId: string } {
  return { mode: "copy", nodeId };
}

export function pasteNodeOperation(
  nodes: NodeDto[],
  clipboard: { mode: "cut" | "copy"; nodeId: string } | null,
  parentId: string | null
): NodeDto[] {
  if (!clipboard) return nodes;

  const sourceNode = nodes.find((n) => n.nodeId === clipboard.nodeId);
  if (!sourceNode) return nodes;

  const sourceSubtree = collectSubtreeIds(nodes, clipboard.nodeId);
  if (sourceSubtree.has(parentId || "")) return nodes;

  const parentNode = parentId ? nodes.find((n) => n.nodeId === parentId) : null;
  if (parentId && parentNode && !canHaveChildren(parentNode.kind)) return nodes;

  const newParentPath = parentNode ? parentNode.path : "";

  const idMap = new Map<string, string>();
  const cloned = cloneSubtree(nodes, clipboard.nodeId, parentId, newParentPath, idMap);

  if (clipboard.mode === "cut") {
    const cutSubIds = collectSubtreeIds(nodes, clipboard.nodeId);
    const remaining = nodes.filter((n) => !cutSubIds.has(n.nodeId));
    return [...remaining, ...cloned];
  } else {
    return [...nodes, ...cloned];
  }
}
