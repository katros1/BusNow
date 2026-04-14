export function Placeholder({ name }: { name: string }) {
  return (
    <div className="flex h-full items-center justify-center text-muted-foreground">
      <p className="text-sm">{name} — coming soon</p>
    </div>
  );
}
