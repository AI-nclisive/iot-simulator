type StaleBannerProps = {
  message: string;
};

export function StaleBanner({ message }: StaleBannerProps) {
  return (
    <div className="mb-4 rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
      {message}
    </div>
  );
}
